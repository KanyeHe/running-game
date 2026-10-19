import {Transport} from './shared/Transport.js';
import {Selection} from './shared/Selection.js';
import {combinationKey, cardText} from './shared/model.js';

const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
const compact = params.get('compact') === '1';
document.body.classList.toggle('compact', compact);
const profile = params.get('profile') || getTabProfile();
const settingsKey = 'luzhou-h5-settings';
const storedSettings = readStorage(settingsKey) || {};
const defaultEndpoint = location.port === '5173' ? `${location.protocol}//${location.hostname}:8080` : location.origin;
const endpoint = (storedSettings.endpoint || defaultEndpoint).replace(/\/$/, '');
const sessionKey = `luzhou-h5:${profile}:${endpoint}`;
const selection = new Selection();
let state;
let connected = false;
let toastTimer;
let scoreBefore;
let shownSettlement;
let retryReady = false;
let pendingReady = false;
let automaticAttempts = 0;
let autoEntered = false;
let serverOffset = 0;
const log = [];
const logged = new Set();
const TYPE_NAMES = {SINGLE:'单张', PAIR:'对子', TRIPLE:'三不带', TRIPLE_SINGLES:'三带散', TRIPLE_PAIR:'三带对', STRAIGHT:'顺子', PAIR_RUN:'连对', PLANE:'飞机', PLANE_SINGLES:'飞机带散', PLANE_PAIRS:'飞机带对', BOMB:'炸弹'};
const ERRORS = {UNAUTHORIZED:'登录会话已失效，请点击“更换测试身份”重新登录。', ROOM_NOT_FOUND:'没有找到这个房间，请确认房间号或先创建房间。', ROOM_EXISTS:'这个房间号已存在，请直接加入。', ROOM_FULL:'房间已满，或牌局已经开始。', INVALID_ROOM_ID:'房间号需要 6–64 位字母、数字或短横线。', STALE_VERSION:'牌桌已更新，正在同步，请重新操作。', OLD_SEQUENCE:'请求已过期，正在恢复牌桌。', SEQUENCE_CONFLICT:'操作序号冲突，请刷新页面恢复。', NOT_IN_ROOM:'当前身份不是这个房间的成员。', GAME_RUNNING:'牌局已开始，请等待本局结束。', NOT_YOUR_TURN:'还没轮到你出牌。', ILLEGAL_COMBINATION:'这个组合当前不能出，请重新选牌。', SERVER_ERROR:'服务端暂时无法处理请求，请稍后重试。'};

function readStorage(key) { try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch { return null; } }
function getTabProfile() {
  // A normal fresh tab gets an independent player; reload retains that tab's identity.
  try {
    let id = sessionStorage.getItem('luzhou-tab');
    if (!id) { id = crypto.randomUUID(); sessionStorage.setItem('luzhou-tab', id); }
    return id;
  } catch { return `tab-${Date.now()}-${Math.random().toString(36).slice(2)}`; }
}
function node(tag, className, text) { const n = document.createElement(tag); if(className) n.className=className; if(text !== undefined) n.textContent=String(text); return n; }
function toast(text) { $('toast').textContent=text; $('toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(() => { $('toast').hidden=true; }, 4200); }
function strength(id) { if(id>=52)return 16+id;const rank=id%13+1;return rank===1?14:rank===2?15:rank; }
function nickname(seat) { return state?.players[seat] === state?.playerId ? `${$('nickname').value || '我'}（我）` : `玩家 ${seat+1}`; }
function describe(c) { const subs=Object.entries(c.wildRanks).map(([id,rank])=>`${cardText(Number(id))}→${({1:'A',11:'J',12:'Q',13:'K'})[rank] || rank}`);return [TYPE_NAMES[c.type] || c.type,...subs].join(' · '); }
function setConnected(value, text) {
  connected=value; $('connection-status').textContent=text; $('status-dot').classList.toggle('connected',value);$('reconnect-button').hidden=value;
  updateActions();
}
function updateActions() {
  const inRoom=!!state;
  $('create-button').disabled=!connected||inRoom;$('join-button').disabled=!connected||inRoom;
  const seat=state?.players.indexOf(state.playerId) ?? -1;
  const active=!!state?.hand && state.winner<0;
  const ready=state?.ready.includes(state.playerId);
  $('ready-button').disabled=!connected||active||ready;
  $('ready-button').textContent=ready?'已准备 · 等待其他玩家':state?.hand?'准备下一局':'准备开局';
  $('next-round').disabled=!connected||ready;
  $('play-button').disabled=!connected||!selection.exact()||state?.turn!==seat||!active;
  $('hint-button').disabled=!connected||!selection.candidates.length;
  $('clear-button').disabled=!selection.selected.size;
}
function ready() {
  if(!connected||!state||state.ready.includes(state.playerId)||(state.hand&&state.winner<0))return;
  pendingReady=true;net.send('READY');
}
function buildCard(id, disabled, selected, onClick, wildRanks) {
  const red=id<52&&[1,3].includes(Math.floor(id/13));
  const n=node(onClick?'button':'div',`card${red?' red':''}${selected?' selected':''}`);
  if(onClick){n.type='button';n.disabled=disabled;n.setAttribute('aria-pressed',String(selected));n.setAttribute('aria-label',`${cardText(id)}${disabled?'，当前不可出':''}`);n.addEventListener('click',onClick);}
  if(id>=52)n.append(node('span','joker',cardText(id)));
  else {const rank=id%13+1;const value=({1:'A',11:'J',12:'Q',13:'K'})[rank]||rank;n.append(node('span','',value),node('span','card-suit',['♠','♥','♣','♦'][Math.floor(id/13)]),node('span','card-mini',value));}
  if(wildRanks?.[id])n.append(node('span','wild-as',`作 ${wildRanks[id]}`));
  return n;
}
function renderHand() {
  const container=$('hand');container.replaceChildren();
  if(!state?.hand){container.append(node('p','empty-hand','开局后，手牌将在这里显示'));return;}
  const cards=[...state.hand].sort((a,b)=>strength(a.id)-strength(b.id)||a.id-b.id);
  cards.forEach(({id})=>container.append(buildCard(id,!selection.enabled(id)||!connected,selection.selected.has(id),()=>{
    const scroll=container.scrollLeft;selection.toggle(id);renderHand();container.scrollLeft=scroll;updateActions();
  })));
  $('hand-title').textContent=`我的手牌 · ${cards.length} 张`;
  const exact=selection.exact();$('selection-description').textContent=exact?describe(exact):selection.selected.size?`已选 ${selection.selected.size} 张 · 继续选择完整组合`:'合法手牌将自动高亮';
}
function renderSeats() {
  const positions=['seat-self','seat-right','seat-top','seat-left'];
  const mine=state?.players.indexOf(state.playerId) ?? 0;
  for(let relative=0;relative<4;relative++) {
    const seat=(mine+relative)%4;const player=state?.players[seat];const element=$(positions[relative]);
    element.className=`seat ${positions[relative]}${player?'':' vacant'}${state?.hand&&state.turn===seat&&state.winner<0?' active':''}`;
    element.replaceChildren(node('div','avatar',player?(relative===0?'我':seat+1):'+'));
    const info=node('div');info.append(node('div','seat-name',player?nickname(seat):'等待入座'));
    let meta='空位';
    if(player){const online=state.online[player];meta=!online?'暂时离线':state.hand?`${state.counts[seat]} 张 · ${state.scores[seat]} 分`:state.ready.includes(player)?'已准备 ✓':'等待准备';}
    info.append(node('div',`seat-meta${player&&!state.online[player]?' offline':''}`,meta));element.append(info);
    if(scoreBefore&&state?.scores&&scoreBefore[seat]!==state.scores[seat]){const delta=state.scores[seat]-scoreBefore[seat];element.append(node('span','score-change',`${delta>0?'+':''}${delta}`));}
  }
  if(state?.scores)scoreBefore=[...state.scores];
}
function renderTable() {
  const active=state?.hand&&state.winner<0;
  $('table-state').textContent=!state?'等待入座':active?'对局进行中':state.hand?'本局已结算':'等待准备';
  $('round-label').textContent=state?.hand?`第 ${state.trick} 轮 · ${state.players.length} 人牌桌`:'四人 · 13 张手牌';
  $('table-idle').hidden=!!state?.table;$('played-area').hidden=!state?.table;
  if(state?.table){$('played-caption').textContent=describe(state.table);$('played-cards').replaceChildren(...state.table.cardIds.map(id=>buildCard(id,false,false,null,state.table.wildRanks)));}
  $('idle-message').textContent=!state?'创建或加入房间，开始这一局':active?`${nickname(state.turn)}领出`:state.hand?'本局已结束，准备好再来一局':`已入座 ${state.players.length}/4 人 · 准备 ${state.ready.length}/4 人`;
  $('turn-countdown').hidden=!active;
  const myTurn=active&&state.players[state.turn]===state.playerId;
  $('action-hint').textContent=!state?'四位玩家准备后，由服务端洗牌发牌。':!state.hand?'点击准备，四人都准备后自动开局。':state.winner>=0?'本局已结束，可准备下一局。':myTurn?'轮到你了：有牌必出，可自由选择合法组合。':`等待${nickname(state.turn)}出牌 · 无牌可压时自动跳过。`;
  $('hint-count').textContent=selection.candidates.length?`(${selection.candidates.length})`:'';
}
function renderRoom() {
  $('room-details').hidden=!state;$('room-form').hidden=!!state;
  if(!state)return;
  $('room-number').textContent=state.roomId;$('room-summary').textContent=`已入座 ${state.players.length}/4 人，已准备 ${state.ready.length}/4 人。`;
}
function renderEvents() {
  if(!state?.events)return;
  const fresh=[];
  state.events.forEach(event=>{
    const id=`${state.roomId}:${state.gameSerial}:${event.version}:${event.type}:${event.player}:${event.trick}:${event.detail}`;
    if(logged.has(id))return;logged.add(id);
    let text;
    if(event.type==='AUTO_SKIPPED')text=`${nickname(event.player)}无牌可压，自动跳过`;
    else if(event.type==='PLAYED')text=`${nickname(event.player)}出牌${event.combination?' · '+describe(event.combination):''}`;
    else if(event.type==='TRICK_ENDED')text=`第 ${event.trick} 轮结束，${nickname(event.player)}继续领出`;
    else if(event.type==='BOMB_SCORE_CHANGED')text='炸弹积分已更新，包含被压过炸弹的冲正';
    else if(event.type==='GAME_STARTED')text='服务端已洗牌发牌，牌局开始';
    else if(event.type==='SETTLED')text=`${nickname(event.player)}出完手牌，本局结束`;
    if(text){log.push(text);fresh.push(text);}
  });
  while(log.length>80)log.shift();
  $('event-log').replaceChildren(...log.slice().reverse().map(text=>node('li','',text)));
  if(fresh.length)$('table-notice').textContent=fresh.slice(-2).join(' · ');
}
function showSettlement() {
  if(!state?.hand||state.winner<0)return;
  // Room version can change after finishing; use the settled event as the stable round identity.
  const event=state.events?.find(e=>e.type==='SETTLED');
  const key=`${state.roomId}:${state.gameSerial}:${event?.version}:${event?.detail}`;
  if(shownSettlement===key)return;shownSettlement=key;
  $('winner-heading').textContent=`${nickname(state.winner)}获胜`;
  $('settlement-scores').replaceChildren(...state.players.map((_,seat)=>{
    const row=node('div','settlement-row');const label=node('span','',nickname(seat));label.append(node('small','',` · 剩 ${state.counts[seat]} 张`));
    const score=state.scores[seat];row.append(label,node('strong',score>0?'positive':score<0?'negative':'',`${score>0?'+':''}${score} 分`));return row;
  }));
  if(!$('settlement').open)$('settlement').showModal();
}
function receive(snapshot) {
  const changed=!state||state.roomId!==snapshot.roomId||state.version!==snapshot.version;
  if(state&&(state.roomId!==snapshot.roomId||state.gameSerial!==snapshot.gameSerial)){
    log.length=0;logged.clear();scoreBefore=undefined;$('table-notice').textContent='';$('settlement').close();
  }
  state=snapshot;
  if(snapshot.serverTime)serverOffset=snapshot.serverTime-Date.now();
  if(changed)selection.reset(snapshot.legal||[]);
  renderRoom();renderSeats();renderTable();renderHand();renderEvents();updateActions();showSettlement();
  if(state.ready.includes(state.playerId)||(state.hand&&state.winner<0))pendingReady=false;
  if(retryReady&&snapshot.type==='SNAPSHOT'){retryReady=false;ready();}
  if(window.parent!==window)window.parent.postMessage({type:'LUZHOU_TEST_STATE',profile,players:state.players.length,ready:state.ready.length,inGame:!!state.hand&&state.winner<0},location.origin);
}

$('nickname').value=params.get('seat')?`测试玩家 ${params.get('seat')}`:storedSettings.name || '游客';
$('room-input').value=params.get('room')||`room-${Math.random().toString(36).slice(2,8)}`;
$('server-input').value=storedSettings.endpoint||'';
const net=new Transport(endpoint,sessionKey);
net.onSnapshot=receive;
net.onStatus=text=>setConnected(net.isConnected,text);
net.onReady=()=>{
  setConnected(true,'已连接 · 实时同步');
  if(params.get('auto')&&!autoEntered){
    autoEntered=true;
    if(readStorage(sessionKey)?.roomId!==params.get('room'))automaticEnter();
  }
};
net.onError=(code)=>{
  if(code==='UNAUTHORIZED'){net.close();localStorage.removeItem(sessionKey);setConnected(false,'会话已失效，点击重新连接登录');}
  if(code==='STALE_VERSION'&&pendingReady)retryReady=true;
  if(code==='ROOM_NOT_FOUND'&&params.get('auto')==='join'&&automaticAttempts<15){setTimeout(automaticEnter,500);return;}
  if(code==='ROOM_NOT_FOUND'&&state){state=undefined;selection.reset([]);renderRoom();renderSeats();renderTable();renderHand();updateActions();}
  if(code==='OLD_SEQUENCE')net.send('SNAPSHOT');
  toast(ERRORS[code]||`操作未完成：${code}`);
};
function automaticEnter(){automaticAttempts++;const id=params.get('room');if(!id)return;if(params.get('auto')==='create')net.create(id);else net.join(id);}
function roomId(){const value=$('room-input').value.trim();if(!/^[a-zA-Z0-9-]{6,64}$/.test(value)){toast(ERRORS.INVALID_ROOM_ID);return;}return value;}
$('create-button').addEventListener('click',()=>{const id=roomId();if(id)net.create(id,$('score-plan').value);});
$('join-button').addEventListener('click',()=>{const id=roomId();if(id)net.join(id);});
$('random-room').addEventListener('click',()=>{$('room-input').value=`room-${Math.random().toString(36).slice(2,8)}`;});
$('ready-button').addEventListener('click',ready);
$('hint-button').addEventListener('click',()=>{selection.hint();renderHand();updateActions();});
$('clear-button').addEventListener('click',()=>{selection.selected.clear();selection.preferred=undefined;renderHand();updateActions();});
$('play-button').addEventListener('click',()=>{const c=selection.exact();if(c){$('play-button').disabled=true;net.send('PLAY',{combinationKey:combinationKey(c)});}});
$('nickname').addEventListener('change',()=>{localStorage.setItem(settingsKey,JSON.stringify({...storedSettings,name:$('nickname').value.trim().slice(0,24)}));});
$('save-server').addEventListener('click',()=>{
  const value=$('server-input').value.trim();
  if(value){try{const url=new URL(value);if(!['http:','https:'].includes(url.protocol)||url.username||url.password)throw new Error();}catch{toast('请输入有效的 http:// 或 https:// 服务端地址');return;}}
  localStorage.setItem(settingsKey,JSON.stringify({...storedSettings,endpoint:value}));location.reload();
});
$('new-identity').addEventListener('click',()=>{net.close();localStorage.removeItem(sessionKey);location.href=`${location.pathname}?profile=${crypto.randomUUID()}`;});
$('reconnect-button').addEventListener('click',()=>location.reload());
$('copy-invite').addEventListener('click',async()=>{
  if(!state)return;const url=new URL(location.pathname,location.origin);url.searchParams.set('room',state.roomId);
  try{await navigator.clipboard.writeText(url.href);toast('邀请链接已复制，好友打开后点击加入房间。');}catch{toast(`邀请链接：${url.href}`);}
});
$('events-toggle').addEventListener('click',()=>{$('event-log').hidden=!$('event-log').hidden;$('events-toggle').textContent=$('event-log').hidden?'对局记录 ↓':'收起记录 ↑';});
$('help-button').addEventListener('click',()=>$('help-dialog').showModal());$('help-close').addEventListener('click',()=>$('help-dialog').close());
$('settlement-close').addEventListener('click',()=>$('settlement').close());
$('next-round').addEventListener('click',()=>{$('settlement').close();ready();});
window.addEventListener('message',event=>{if(event.origin===location.origin&&event.source===window.parent&&event.data?.type==='LUZHOU_TEST_READY')ready();});
window.addEventListener('pagehide',()=>net.close());
setInterval(()=>{
  if(!state?.deadline||state.winner>=0)return;
  const remaining=Math.max(0,Math.ceil((state.deadline-Date.now()-serverOffset)/1000));
  const own=state.players[state.turn]===state.playerId;
  $('turn-countdown').textContent=remaining?`${own?'你的回合':nickname(state.turn)} · ${remaining} 秒`:'等待出牌 · 不会自动过牌';
  $('turn-countdown').classList.toggle('urgent',remaining>0&&remaining<=5);
},250);
renderSeats();updateActions();
void net.start($('nickname').value||'游客').catch(()=>{setConnected(false,'未连上服务端');toast(`无法连接 ${endpoint}。请先启动 Java 服务，或检查连接设置。`);});
