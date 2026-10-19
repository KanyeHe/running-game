// Generated from client/assets/scripts; edit shared TypeScript source.
function getWx() { return typeof wx === 'undefined' ? undefined : wx; }
function socket(url) {
    const api = getWx();
    if (api) {
        const task = api.connectSocket({ url });
        const result = { send: data => task.send({ data }), close: () => task.close(), onopen: null, onmessage: null, onclose: null };
        task.onOpen(() => result.onopen?.());
        task.onMessage(r => result.onmessage?.(r.data));
        task.onClose(() => result.onclose?.());
        task.onError(() => { task.close(); });
        return result;
    }
    const task = new WebSocket(url);
    const result = { send: data => task.send(data), close: () => task.close(), onopen: null, onmessage: null, onclose: null };
    task.onopen = () => result.onopen?.();
    task.onmessage = r => result.onmessage?.(String(r.data));
    task.onclose = () => result.onclose?.();
    return result;
}
export class Transport {
    get isConnected() { return this.authenticated; }
    constructor(endpoint, storageKey = 'luzhou-session') {
        this.endpoint = endpoint;
        this.storageKey = storageKey;
        this.onSnapshot = () => { };
        this.onStatus = () => { };
        this.onReady = () => { };
        this.onError = () => { };
        this.stopped = false;
        this.pending = new Map();
        this.seq = 0;
        this.token = '';
        this.playerId = '';
        this.version = 0;
        this.roomId = '';
        this.authenticated = false;
        this.retry = 0;
    }
    load() {
        try {
            const api = getWx();
            const text = api ? api.getStorageSync(this.storageKey) : localStorage.getItem(this.storageKey);
            if (!text)
                return;
            const value = typeof text === 'string' ? JSON.parse(text) : text;
            this.token = value.token ?? '';
            this.playerId = value.playerId ?? '';
            this.seq = value.seq ?? 0;
            this.roomId = value.roomId ?? '';
        }
        catch {
            this.onStatus('无法读取本地会话');
        }
    }
    persist() {
        const text = JSON.stringify({ token: this.token, playerId: this.playerId, seq: this.seq, roomId: this.roomId });
        const api = getWx();
        if (api)
            api.setStorageSync(this.storageKey, text);
        else
            localStorage.setItem(this.storageKey, text);
    }
    async start(name) {
        this.load();
        if (!this.token) {
            const url = `${this.endpoint}/api/login`, data = { name };
            let identity;
            const api = getWx();
            if (api)
                identity = await new Promise((resolve, reject) => api.request({ url, method: 'POST', data, success: r => r.statusCode === 200 ? resolve(r.data) : reject(new Error('登录失败')), fail: reject }));
            else {
                const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
                if (!response.ok)
                    throw new Error('登录失败');
                identity = await response.json();
            }
            this.token = identity.token;
            this.playerId = identity.playerId;
            this.persist();
        }
        this.connect();
    }
    connect() {
        if (this.stopped)
            return;
        this.onStatus('正在连接');
        this.authenticated = false;
        const channel = socket(`${this.endpoint.replace(/^http/, 'ws')}/ws`);
        this.channel = channel;
        channel.onopen = () => channel.send(JSON.stringify({ type: 'AUTH', requestId: 'auth', seq: 1, expectedVersion: 0, token: this.token, data: {} }));
        channel.onmessage = text => {
            let value;
            try {
                value = JSON.parse(text);
            }
            catch {
                this.onStatus('收到无效消息');
                return;
            }
            if (value.type === 'LOGIN_SUCCESS') {
                this.authenticated = true;
                this.retry = 0;
                this.onStatus('已连接');
                // Retries use exactly the original request id, sequence and expected version.
                for (const q of [...this.pending.values()])
                    channel.send(JSON.stringify(q));
                if (this.roomId)
                    this.send('RECONNECT', {}, this.roomId);
                this.onReady();
                return;
            }
            if (value.requestId)
                this.pending.delete(value.requestId);
            if (value.type === 'ERROR') {
                this.onStatus(value.code ?? '请求失败');
                this.onError(value.code ?? 'SERVER_ERROR', value.requestId);
                if (value.code === 'STALE_VERSION' && this.roomId)
                    this.send('SNAPSHOT');
                return;
            }
            if (typeof value.version === 'number' && value.version >= this.version) {
                this.version = value.version;
                this.roomId = value.roomId;
                this.persist();
                this.onSnapshot(value);
            }
        };
        channel.onclose = () => {
            this.authenticated = false;
            this.onStatus('连接中断，正在重连');
            if (!this.stopped)
                this.reconnectTimer = setTimeout(() => this.connect(), Math.min(10000, 500 * 2 ** this.retry++));
        };
    }
    send(type, data = {}, roomId = this.roomId) {
        if (!this.authenticated) {
            this.onStatus('请等待连接恢复');
            return;
        }
        const q = { type, requestId: `${this.playerId}-${++this.seq}`, seq: this.seq, roomId, expectedVersion: this.version, data };
        this.persist();
        if (type !== 'SNAPSHOT' && type !== 'HINT')
            this.pending.set(q.requestId, q);
        this.channel?.send(JSON.stringify(q));
    }
    create(roomId, scorePlan = 'low') { this.version = 1; this.send('CREATE_ROOM', { scorePlan }, roomId); }
    join(roomId) { this.version = 0; this.send('JOIN_ROOM', {}, roomId); }
    close() { this.stopped = true; clearTimeout(this.reconnectTimer); this.channel?.close(); }
}
