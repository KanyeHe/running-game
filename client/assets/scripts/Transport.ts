import {Command, Snapshot} from './model';

interface Socket {
  onopen: (() => void) | null;
  onmessage: ((text: string) => void) | null;
  onclose: (() => void) | null;

  send(data: string): void;

  close(): void;
}

interface WxTask {
  send(options: { data: string }): void;

  close(): void;

  onOpen(fn: () => void): void;

  onMessage(fn: (res: { data: string }) => void): void;

  onClose(fn: () => void): void;

  onError(fn: () => void): void;
}

interface WxApi {
  connectSocket(options: { url: string }): WxTask;

  request(options: {
    url: string;
    method: string;
    data: unknown;
    success: (r: { statusCode: number; data: unknown }) => void;
    fail: (e: unknown) => void
  }): void;

  getStorageSync(key: string): unknown;

  setStorageSync(key: string, value: unknown): void;
}

declare const wx: WxApi | undefined;

function getWx(): WxApi | undefined {
  return typeof wx === 'undefined' ? undefined : wx;
}

// 两种运行平台遵守同一个小接口，上层重连逻辑不依赖具体 Socket 实现。
function socket(url: string): Socket {
  const api = getWx();
  return api ? wxSocket(api, url) : browserSocket(url);
}

function wxSocket(api: WxApi, url: string): Socket {
  const task = api.connectSocket({url});
  const result: Socket = {
    send: data => task.send({data}), close: () => task.close(),
    onopen: null, onmessage: null, onclose: null
  };
  task.onOpen(() => result.onopen?.());
  task.onMessage(r => result.onmessage?.(r.data));
  task.onClose(() => result.onclose?.());
  task.onError(() => task.close());
  return result;
}

function browserSocket(url: string): Socket {
  const task = new WebSocket(url);
  const result: Socket = {
    send: data => task.send(data), close: () => task.close(),
    onopen: null, onmessage: null, onclose: null
  };
  task.onopen = () => result.onopen?.();
  task.onmessage = r => result.onmessage?.(String(r.data));
  task.onclose = () => result.onclose?.();
  return result;
}

export class Transport {
  private channel?: Socket;
  private stopped = false;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private pending = new Map<string, Command>();
  private seq = 0;
  private token = '';
  private playerId = '';
  private version = 0;
  private roomId = '';
  private authenticated = false;
  private retry = 0;

  constructor(private readonly endpoint: string, private readonly storageKey = 'luzhou-session') {
  }

  get isConnected(): boolean {
    return this.authenticated;
  }

  onSnapshot: (snapshot: Snapshot) => void = () => {
  };

  onStatus: (text: string) => void = () => {
  };

  onReady: () => void = () => {
  };

  onError: (code: string, requestId?: string) => void = () => {
  };

  async start(name: string): Promise<void> {
    this.load();
    if (!this.token) {
      const identity = await this.login(name);
      this.token = identity.token;
      this.playerId = identity.playerId;
      this.persist();
    }
    this.connect();
  }

  private async login(name: string): Promise<{token: string; playerId: string}> {
    const url = `${this.endpoint}/api/login`, data = {name};
    const api = getWx();
    if (api) return new Promise((resolve, reject) => api.request({
      url, method: 'POST', data,
      success: r => r.statusCode === 200
        ? resolve(r.data as {token: string; playerId: string}) : reject(new Error('登录失败')),
      fail: reject
    }));
    const response = await fetch(url, {
      method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)
    });
    if (!response.ok) throw new Error('登录失败');
    return response.json();
  }

  send(type: string, data: Record<string, string> = {}, roomId = this.roomId): void {
    if (!this.authenticated) {
      this.onStatus('请等待连接恢复');
      return;
    }
    const q: Command = {
      type,
      requestId: `${this.playerId}-${++this.seq}`,
      seq: this.seq,
      roomId,
      expectedVersion: this.version,
      data
    };
    this.persist();
    if (type !== 'SNAPSHOT' && type !== 'HINT') this.pending.set(q.requestId, q);
    this.channel?.send(JSON.stringify(q));
  }

  create(roomId: string, scorePlan = 'low'): void {
    this.version = 1;
    this.send('CREATE_ROOM', {scorePlan}, roomId);
  }

  join(roomId: string): void {
    this.version = 0;
    this.send('JOIN_ROOM', {}, roomId);
  }

  close(): void {
    this.stopped = true;
    this.authenticated = false;
    clearTimeout(this.reconnectTimer);
    this.channel?.close();
  }

  private load(): void {
    try {
      const api = getWx();
      const text = api ? api.getStorageSync(this.storageKey) : localStorage.getItem(this.storageKey);
      if (!text) return;
      const value = typeof text === 'string' ? JSON.parse(text) : text;
      this.token = value.token ?? '';
      this.playerId = value.playerId ?? '';
      this.seq = value.seq ?? 0;
      this.roomId = value.roomId ?? '';
    } catch {
      this.onStatus('无法读取本地会话');
    }
  }

  private persist(): void {
    const text = JSON.stringify({
      token: this.token,
      playerId: this.playerId,
      seq: this.seq,
      roomId: this.roomId
    });
    const api = getWx();
    if (api) api.setStorageSync(this.storageKey, text); else localStorage.setItem(this.storageKey, text);
  }

  private connect(): void {
    if (this.stopped) return;
    this.onStatus('正在连接');
    this.authenticated = false;
    const channel = socket(`${this.endpoint.replace(/^http/, 'ws')}/ws`);
    this.channel = channel;
    channel.onopen = () => channel.send(JSON.stringify({
      type: 'AUTH',
      requestId: 'auth',
      seq: 1,
      expectedVersion: 0,
      token: this.token,
      data: {}
    }));
    channel.onmessage = text => this.receive(channel, text);
    channel.onclose = () => {
      if (channel !== this.channel || this.stopped) return;
      this.authenticated = false;
      this.onStatus('连接中断，正在重连');
      if (!this.stopped) this.reconnectTimer = setTimeout(() => this.connect(), Math.min(10000, 500 * 2 ** this.retry++));
    };
  }

  private receive(channel: Socket, text: string): void {
    // 已替换连接的迟到事件不能覆盖当前连接的认证和快照。
    if (channel !== this.channel || this.stopped) return;
    let value: Snapshot;
    try {
      value = JSON.parse(text) as Snapshot;
    } catch {
      this.onStatus('收到无效消息');
      return;
    }
    if (value.type === 'LOGIN_SUCCESS') {
      this.resume(channel);
      return;
    }
    if (value.requestId) this.pending.delete(value.requestId);
    if (value.type === 'ERROR') {
      this.onStatus(value.code ?? '请求失败');
      this.onError(value.code ?? 'SERVER_ERROR', value.requestId);
      if (value.code === 'STALE_VERSION' && this.roomId) this.send('SNAPSHOT');
      return;
    }
    if (typeof value.version === 'number' && value.version >= this.version) {
      this.version = value.version;
      this.roomId = value.roomId;
      this.persist();
      this.onSnapshot(value);
    }
  }

  private resume(channel: Socket): void {
    this.authenticated = true;
    this.retry = 0;
    this.onStatus('已连接');
    // 先原样重放未确认变更，再递增序号发 RECONNECT，防止丢失已提交回执。
    for (const q of [...this.pending.values()]) channel.send(JSON.stringify(q));
    if (this.roomId) this.send('RECONNECT', {}, this.roomId);
    this.onReady();
  }
}
