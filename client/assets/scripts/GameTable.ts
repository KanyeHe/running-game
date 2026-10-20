import {
  _decorator,
  Color,
  Component,
  EditBox,
  Graphics,
  Label,
  Layers,
  Node,
  tween,
  UITransform,
  Vec3
} from 'cc';
import {Transport} from './Transport';
import {cardText, Combination, combinationKey, Snapshot} from './model';
import {Selection} from './Selection';

const {ccclass, property} = _decorator;

@ccclass('GameTable')
export class GameTable extends Component {
  @property serverUrl = 'http://localhost:8080';
  private net!: Transport;
  private state?: Snapshot;
  private selection = new Selection();
  private content!: Node;
  private status!: Label;
  private clock?: Label;
  private roomInput?: EditBox;
  private offset = 0;
  private selectedDescription = '';
  private eventCursor = '';
  private scoreBefore?: number[];

  onLoad(): void {
    this.content = this.box(this.node, 'Content', 0, 0, 1280, 720, '#103e39');
    this.status = this.text(this.node, '', 0, 335, 19, '#bce3d1');
    this.net = new Transport(this.serverUrl);
    this.net.onStatus = text => {
      this.status.string = text;
    };
    this.net.onSnapshot = s => this.receive(s);
    this.lobby();
    void this.net.start('游客').catch(e => {
      this.status.string = String(e);
    });
    this.schedule(() => {
      if (this.clock && this.state?.deadline) {
        const remain = Math.max(0, Math.ceil((this.state.deadline - Date.now() - this.offset) / 1000));
        this.clock.string = remain ? `${remain} 秒` : '等待玩家出牌';
      }
    }, 0.25);
  }

  onDestroy(): void {
    this.net.close();
  }

  private receive(s: Snapshot): void {
    const changed = !this.state || s.version !== this.state.version;
    this.state = s;
    if (s.serverTime) this.offset = s.serverTime - Date.now();
    if (changed) {
      this.selection.reset(s.legal ?? []);
      this.selectedDescription = '';
    }
    this.render();
  }

  private lobby(): void {
    this.content.removeAllChildren();
    this.text(this.content, '泸州跑得快', 0, 180, 58, '#f6dba1');
    this.text(this.content, '四人牌桌 · 娱乐积分', 0, 120, 24, '#bce3d1');
    this.text(this.content, '演示规则 demo-v1 · 部分规则待确认', 0, -210, 18, '#bce3d1');
    const field = this.box(this.content, 'Room', 0, 25, 400, 60, '#e8f2e9');
    const edit = field.addComponent(EditBox);
    const t = this.text(field, '', 0, 0, 24, '#153d36');
    edit.textLabel = t;
    const placeholder = this.text(field, '输入房间号（至少 6 位）', 0, 0, 22, '#658579');
    edit.placeholderLabel = placeholder;
    edit.maxLength = 64;
    this.roomInput = edit;
    this.button(this.content, '创建房间', -130, -70, () => {
      const id = this.roomInput?.string.trim() || `room-${Date.now().toString(36)}`;
      this.net.create(id);
    });
    this.button(this.content, '加入房间', 130, -70, () => this.net.join(this.roomInput?.string.trim() ?? ''));
  }

  private render(): void {
    const s = this.state;
    if (!s) return;
    this.content.removeAllChildren();
    this.clock = undefined;
    this.text(this.content, `泸州跑得快  /  ${s.roomId}`, 0, 285, 28, '#f6dba1');
    const mine = s.players.indexOf(s.playerId);
    const positions = [[-480, -282], [-475, 70], [0, 218], [475, 70]];
    this.renderSeats(s, mine, positions);
    this.scoreBefore = s.scores ? [...s.scores] : undefined;
    if (!s.hand) {
      this.text(this.content, `等待玩家 ${s.players.length}/4`, 0, 50, 30, '#ffffff');
      this.button(this.content, '准备开局', 0, -50, () => this.net.send('READY'));
      return;
    }
    this.clock = this.text(this.content, '', 0, 130, 24, '#f6dba1');
    if (s.table) this.showCombo(s.table);
    else this.text(this.content, s.turn === mine ? '请选择领出的牌' : '等待领出', 0, 40, 26, '#c7ded0');
    this.renderEvents(s);
    this.renderHand(s);
    this.renderActions(s);
    this.renderSettlement(s);
  }

  private renderSeats(s: Snapshot, mine: number, positions: number[][]): void {
    s.players.forEach((p, seat) => {
      const relative = (seat - mine + 4) % 4;
      const [x, y] = positions[relative];
      const panel = this.box(this.content, `Seat${seat}`, x, y, 200, 74, s.turn === seat ? '#b58e43' : '#20584d');
      this.text(panel, `${seat === mine ? '我' : `玩家 ${seat + 1}`}  ${s.online[p] ? '在线' : '离线'}`, 0, 14, 19, '#ffffff');
      this.text(panel, `${s.counts?.[seat] ?? 0} 张  /  ${s.scores?.[seat] ?? 0} 分 ${s.ready.includes(p) ? '已准备' : ''}`, 0, -16, 17, '#d6e5d9');
      if (this.scoreBefore && s.scores && this.scoreBefore[seat] !== s.scores[seat]) {
        const delta = s.scores[seat] - this.scoreBefore[seat];
        const pop = this.text(panel, `${delta > 0 ? '+' : ''}${delta}`, 0, 45, 30, '#ffe39c');
        tween(pop.node).to(0.8, {position: new Vec3(0, 95, 0)}).call(() => pop.node.destroy()).start();
      }
    });
  }

  private renderEvents(s: Snapshot): void {
    const event = s.events?.[s.events.length - 1];
    const skipped = s.events?.filter(e => e.type === 'AUTO_SKIPPED').map(e => `玩家 ${e.player + 1} 自动跳过`).join('  ');
    if (skipped) this.text(this.content, skipped, 0, -45, 18, '#bce3d1');
    if (event) this.eventCursor = `${event.version}:${event.type}`;
  }

  private renderHand(s: Snapshot): void {
    const hand = [...(s.hand ?? [])].sort((a, b) => this.strength(a.id) - this.strength(b.id) || a.id - b.id);
    hand.forEach((card, i) => {
      const enabled = this.selection.enabled(card.id);
      const selected = this.selection.selected.has(card.id);
      const x = (i - (hand.length - 1) / 2) * 61;
      const n = this.box(this.content, `Card${card.id}`, x, selected ? -165 : -185, 72, 108, enabled ? '#fff9e8' : '#728f85');
      this.text(n, cardText(card.id), 0, 20, 24, card.id < 52 && [1, 3].includes(Math.floor(card.id / 13)) ? '#bc3c39' : '#162e2b');
      if (enabled) n.on(Node.EventType.TOUCH_END, () => {
        this.selection.toggle(card.id);
        this.selectedDescription = '';
        this.render();
      });
    });
  }

  private renderActions(s: Snapshot): void {
    this.text(this.content, this.selectedDescription, 0, -95, 18, '#ffe5a5');
    this.button(this.content, '提示', -120, -292, () => {
      const c = this.selection.hint();
      this.selectedDescription = c ? this.describe(c) : '当前没有合法组合';
      this.render();
    }, (s.legal?.length ?? 0) > 0);
    this.button(this.content, '出牌', 120, -292, () => {
      const c = this.selection.exact();
      if (c) this.net.send('PLAY', {combinationKey: combinationKey(c)});
    }, !!this.selection.exact());
  }

  private renderSettlement(s: Snapshot): void {
    if ((s.winner ?? -1) >= 0) {
      const overlay = this.box(this.content, 'Settlement', 0, 0, 680, 360, '#173e39');
      this.text(overlay, `玩家 ${(s.winner ?? 0) + 1} 获胜`, 0, 120, 40, '#f6dba1');
      s.players.forEach((_, i) => this.text(overlay, `玩家 ${i + 1}：剩 ${s.counts?.[i]} 张  ${s.scores?.[i]} 分`, 0, 65 - i * 35, 23, '#ffffff'));
      this.button(overlay, '准备下一局', 0, -125, () => this.net.send('READY'));
    }
  }

  private strength(id: number): number {
    if (id >= 52) return 16 + id;
    const r = id % 13 + 1;
    return r === 1 ? 14 : r === 2 ? 15 : r;
  }

  private describe(c: Combination): string {
    return `${c.type} ${Object.entries(c.wildRanks).map(([id, r]) => `${cardText(Number(id))}→${r}`).join(' ')}`;
  }

  private showCombo(c: Combination): void {
    this.text(this.content, this.describe(c), 0, 90, 18, '#f6dba1');
    c.cardIds.forEach((id, i) => {
      const n = this.box(this.content, 'Played', (i - (c.cardIds.length - 1) / 2) * 42, 28, 48, 70, '#fff6e2');
      this.text(n, cardText(id), 0, 5, 17, '#163e36');
    });
  }

  private box(parent: Node, name: string, x: number, y: number, width: number, height: number, color: string): Node {
    const n = new Node(name);
    n.layer = Layers.Enum.UI_2D;
    n.setParent(parent);
    n.setPosition(x, y);
    n.addComponent(UITransform).setContentSize(width, height);
    const g = n.addComponent(Graphics);
    g.fillColor = new Color().fromHEX(color);
    g.roundRect(-width / 2, -height / 2, width, height, 10);
    g.fill();
    return n;
  }

  private text(parent: Node, text: string, x: number, y: number, size: number, color: string): Label {
    const n = new Node('Text');
    n.layer = Layers.Enum.UI_2D;
    n.setParent(parent);
    n.setPosition(x, y);
    n.addComponent(UITransform).setContentSize(1000, size * 1.6);
    const l = n.addComponent(Label);
    l.string = text;
    l.fontSize = size;
    l.lineHeight = size * 1.3;
    l.color = new Color().fromHEX(color);
    l.horizontalAlign = Label.HorizontalAlign.CENTER;
    return l;
  }

  private button(parent: Node, title: string, x: number, y: number, fn: () => void, enabled = true): void {
    const n = this.box(parent, title, x, y, 190, 52, enabled ? '#e5bd73' : '#547267');
    this.text(n, title, 0, 0, 23, '#153e35');
    if (enabled) n.on(Node.EventType.TOUCH_END, fn);
  }
}
