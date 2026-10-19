const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require(process.argv[2] || '../client/node_modules/typescript');
const base = path.resolve(__dirname, '../client/assets/scripts');
const modules = new Map();
function load(name) {
  const file = path.resolve(base, name + '.ts');
  if (modules.has(file)) return modules.get(file);
  const source = fs.readFileSync(file, 'utf8');
  const result = ts.transpileModule(source, {compilerOptions: {target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS}});
  const module = {exports: {}};
  new Function('require', 'module', 'exports', result.outputText)(id => load(id), module, module.exports);
  modules.set(file, module.exports);return module.exports;
}
const {Selection} = load('Selection');const {combinationKey} = load('model');
const one = {type:'PAIR', cardIds:[1,52], wildRanks:{52:2}, value:15, groups:1, bodyRank:2, basis:'test'};
const two = {type:'SINGLE', cardIds:[7], wildRanks:{}, value:8, groups:1, bodyRank:8, basis:'test'};
assert.equal(combinationKey(one),'PAIR:[1, 52]:{52=2}:15:1:2');
const selection = new Selection();selection.reset([one,two]);
assert.equal(selection.enabled(1),true);assert.equal(selection.enabled(20),false);
selection.toggle(1);selection.toggle(7);assert.deepEqual([...selection.selected],[1]);assert.equal(selection.exact(),undefined);
selection.toggle(52);assert.equal(selection.exact(),one);
selection.reset([one,two]);assert.equal(selection.hint(),one);assert.equal(selection.hint(),two);assert.equal(selection.hint(),one);
selection.reset([]);assert.equal(selection.hint(),undefined);assert.equal(selection.exact(),undefined);
// Transport reconnect must replay the original mutation before advancing the sequence via RECONNECT.
const {Transport} = load('Transport');
let nextSocket;
global.localStorage={getItem:()=>null,setItem:()=>{}};
global.WebSocket=class {
  constructor(){this.sent=[];nextSocket=this;}
  send(text){this.sent.push(JSON.parse(text));}
  close(){}
};
const t=new Transport('http://localhost:8080');
t.token='test';t.playerId='player';t.roomId='test-room';t.seq=8;
const mutation={type:'PLAY',requestId:'original',seq:8,roomId:'test-room',expectedVersion:12,data:{combinationKey:'x'}};
t.pending.set('original',mutation);t.connect();
nextSocket.onopen();nextSocket.onmessage({data:JSON.stringify({type:'LOGIN_SUCCESS'})});
assert.equal(nextSocket.sent[0].type,'AUTH');assert.deepEqual(nextSocket.sent[1],mutation);assert.equal(nextSocket.sent[2].type,'RECONNECT');
assert.equal(nextSocket.sent[2].seq,9);t.close();
console.log('PASS client selection, hint cycle, Java key compatibility, reconnect replay order');
