const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root,'src/app.js'),'utf8');

test('H5 page contains every DOM control used by the application', () => {
  const html=fs.readFileSync(path.join(root,'src/index.html'),'utf8');
  const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]);
  assert.equal(ids.length,new Set(ids).size,'unique DOM ids');
  for(const [,id] of source.matchAll(/\$\('([^']+)'\)/g))assert.ok(ids.includes(id),`missing ${id}`);
  assert.ok(!html.includes('http://fonts'),'no external font dependency');
  assert.ok(html.includes('type="module"'));
});

test('static artifacts share the original rule interaction source and have browser-resolvable imports', () => {
  for(const file of ['model','Selection','Transport']){
    const output=fs.readFileSync(path.join(root,'dist/shared',file+'.js'),'utf8');
    for(const [,specifier] of output.matchAll(/from\s+['"]([^'"]+)['"]/g))assert.ok(specifier.endsWith('.js'),specifier);
    assert.equal(output,fs.readFileSync(path.join(root,'../server/src/main/resources/static/shared',file+'.js'),'utf8'));
  }
  for(const file of ['index.html','app.css','app.js','test.html','test.js'])assert.equal(fs.readFileSync(path.join(root,'src',file),'utf8'),fs.readFileSync(path.join(root,'dist',file),'utf8'));
});

test('selection keeps physical IDs and loops every server candidate', async () => {
  const {Selection}=await import(pathToFileURL(path.join(root,'dist/shared/Selection.js')));
  const {combinationKey}=await import(pathToFileURL(path.join(root,'dist/shared/model.js')));
  const selection=new Selection();
  const a={type:'PAIR',cardIds:[1,52],wildRanks:{52:2},value:15,groups:1,bodyRank:2,basis:'server'};
  const b={type:'SINGLE',cardIds:[8],wildRanks:{},value:9,groups:1,bodyRank:9,basis:'server'};
  selection.reset([a,b]);selection.toggle(1);selection.toggle(8);
  assert.deepEqual([...selection.selected],[1]);assert.equal(selection.exact(),undefined);
  selection.toggle(52);assert.equal(selection.exact(),a);
  assert.equal(combinationKey(a),'PAIR:[1, 52]:{52=2}:15:1:2');
  assert.equal(selection.hint(),a);assert.equal(selection.hint(),b);assert.equal(selection.hint(),a);
});

test('four browser profiles authenticate independently and restore their own room and sequence', async () => {
  const {Transport}=await import(pathToFileURL(path.join(root,'dist/shared/Transport.js')));
  const original={fetch:global.fetch,WebSocket:global.WebSocket,localStorage:global.localStorage};
  const storage=new Map();const sockets=[];let identities=0;
  global.localStorage={getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value)};
  global.fetch=async()=>({ok:true,json:async()=>{const n=++identities;return {playerId:`player-${n}`,token:`token-${n}`};}});
  global.WebSocket=class {
    constructor(url){this.url=url;this.sent=[];sockets.push(this);}
    send(text){this.sent.push(JSON.parse(text));}
    close(){}
    receive(value){this.onmessage({data:JSON.stringify(value)});}
  };
  const clients=[];
  try {
    for(let i=0;i<4;i++){
      const client=new Transport('http://localhost:8080',`profile-${i}`);clients.push(client);let ready=0;
      client.onReady=()=>ready++;await client.start(`玩家${i}`);
      const socket=sockets[i];socket.onopen();assert.equal(socket.sent[0].token,`token-${i+1}`);
      socket.receive({type:'LOGIN_SUCCESS'});assert.equal(ready,1);assert.equal(client.isConnected,true);
      if(i===0)client.create('test-room');else client.join('test-room');
      socket.receive({type:'ACK',requestId:socket.sent.at(-1).requestId,roomId:'test-room',version:2+i,playerId:`player-${i+1}`});
    }
    assert.equal(new Set(sockets.map(s=>s.sent[0].token)).size,4);
    const restored=new Transport('http://localhost:8080','profile-2');clients.push(restored);await restored.start('恢复玩家');
    const socket=sockets.at(-1);socket.onopen();assert.equal(socket.sent[0].token,'token-3');
    socket.receive({type:'LOGIN_SUCCESS'});assert.equal(socket.sent[1].type,'RECONNECT');assert.equal(socket.sent[1].roomId,'test-room');
    assert.equal(identities,4,'refresh must not create another identity');
    let error;restored.onError=code=>{error=code;};socket.receive({type:'ERROR',code:'STALE_VERSION',requestId:socket.sent[1].requestId});
    assert.equal(error,'STALE_VERSION');assert.equal(socket.sent.at(-1).type,'SNAPSHOT');
  }finally{clients.forEach(c=>c.close());Object.assign(global,original);}
});

test('the four-window page uses explicit identity namespaces and guards messages by origin',()=>{
  const script=fs.readFileSync(path.join(root,'src/test.js'),'utf8');
  assert.ok(script.includes('profile:`${testId}-${i}`'));
  assert.ok(script.includes('event.origin!==location.origin'));
  assert.ok(script.includes('frame.contentWindow===event.source'));
  assert.ok(script.includes("i===1?'create':'join'"));
});

test('page reacts to a private snapshot, selects a server candidate and submits the exact key',async()=>{
  const vm=require('node:vm');
  const {Selection}=await import(pathToFileURL(path.join(root,'dist/shared/Selection.js')));
  const {combinationKey,cardText}=await import(pathToFileURL(path.join(root,'dist/shared/model.js')));
  class Element {
    constructor(){this.children=[];this.listeners={};this.value='';this.hidden=false;this.disabled=false;this.open=false;this.classList={toggle(){}};}
    append(...children){this.children.push(...children);}replaceChildren(...children){this.children=children;}
    setAttribute(){}addEventListener(type,fn){this.listeners[type]=fn;}
    click(){if(!this.disabled)this.listeners.click?.();}showModal(){this.open=true;}close(){this.open=false;}
  }
  const html=fs.readFileSync(path.join(root,'src/index.html'),'utf8');
  const elements=Object.fromEntries([...html.matchAll(/\bid="([^"]+)"/g)].map(([,id])=>[id,new Element()]));
  elements['score-plan'].value='low';
  const document={getElementById:id=>elements[id]||null,createElement:()=>new Element(),body:new Element()};
  let transport;
  class Transport {
    constructor(){transport=this;this.commands=[];this.isConnected=false;}
    async start(){this.isConnected=true;this.onReady();}
    send(type,data={}){this.commands.push({type,data});}create(){}join(){}close(){}
  }
  const store=new Map();const storage={getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)};
  const window={addEventListener(){}};window.parent=window;
  const context={document,window,Transport,Selection,combinationKey,cardText,localStorage:storage,sessionStorage:storage,
    location:{search:'',origin:'http://localhost:8080',protocol:'http:',hostname:'localhost',pathname:'/',port:'8080'},
    URL,URLSearchParams,crypto:require('node:crypto').webcrypto,console,navigator:{},setTimeout:()=>1,clearTimeout(){},setInterval(){}};
  vm.runInNewContext(source.replace(/^import .*;\n/gm,''),context);
  assert.equal(elements['create-button'].disabled,false);
  const lobby={type:'ACK',roomId:'room-123',version:3,playerId:'a',players:['a','b','c','d'],ready:[],online:{a:true,b:true,c:true,d:true}};
  transport.onSnapshot(lobby);elements['ready-button'].click();assert.equal(transport.commands.at(-1).type,'READY');
  const beforeRetry=transport.commands.length;transport.onError('STALE_VERSION');
  transport.onSnapshot({...lobby,type:'SNAPSHOT',version:4,ready:['b']});
  assert.equal(transport.commands.length,beforeRetry+1,'a stale READY is retried after its authoritative snapshot');
  assert.equal(transport.commands.at(-1).type,'READY');
  const candidate={type:'PAIR',cardIds:[1,52],wildRanks:{52:2},value:15,groups:1,bodyRank:2};
  const snapshot={type:'ACK',roomId:'room-123',version:5,playerId:'a',players:['a','b','c','d'],ready:[],online:{a:true,b:true,c:true,d:true},hand:[{id:1},{id:52}],counts:[2,13,13,13],scores:[0,0,0,0],turn:0,table:null,trick:1,winner:-1,deadline:Date.now()+30000,legal:[candidate],events:[]};
  transport.onSnapshot(snapshot);
  assert.equal(elements['room-number'].textContent,'room-123');assert.equal(elements['room-form'].hidden,true);
  assert.equal(elements.hand.children.length,2);assert.equal(elements['play-button'].disabled,true);
  elements['hint-button'].click();assert.equal(elements['play-button'].disabled,false);
  elements['play-button'].click();assert.equal(transport.commands.at(-1).type,'PLAY');assert.equal(transport.commands.at(-1).data.combinationKey,combinationKey(candidate));
  transport.onSnapshot({...snapshot,version:6,turn:1,legal:[]});assert.equal(elements['play-button'].disabled,true);assert.equal(elements['hint-button'].disabled,true);
  transport.onSnapshot({...snapshot,version:7,winner:0,counts:[0,13,13,13],scores:[18,-6,-6,-6],hand:[],legal:[],events:[{type:'SETTLED',version:3,detail:'18,-6,-6,-6'}]});
  assert.equal(elements.settlement.open,true);assert.equal(elements['settlement-scores'].children.length,4);
  elements['next-round'].click();assert.equal(transport.commands.at(-1).type,'READY');assert.equal(elements.settlement.open,false);
  // Identical statistics in a later game must still show a new settlement.
  transport.onSnapshot({...snapshot,version:8,gameSerial:2});
  transport.onSnapshot({...snapshot,version:9,gameSerial:2,winner:0,counts:[0,13,13,13],scores:[18,-6,-6,-6],hand:[],legal:[],events:[{type:'SETTLED',version:3,detail:'18,-6,-6,-6'}]});
  assert.equal(elements.settlement.open,true);
});
