const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {pathToFileURL} = require('node:url');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'src/app.js'), 'utf8');

async function shared() {
  const load = name => import(pathToFileURL(path.join(root, 'dist/shared', name + '.js')));
  return {...await load('Selection'), ...await load('model'), ...await load('Transport')};
}

function pair() {
  return {type: 'PAIR', cardIds: [1, 52], wildRanks: {52: 2}, value: 15,
    groups: 1, bodyRank: 2, basis: 'server'};
}

function single() {
  return {type: 'SINGLE', cardIds: [8], wildRanks: {}, value: 9,
    groups: 1, bodyRank: 9, basis: 'server'};
}

function storage() {
  const values = new Map();
  return {getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key)};
}

class FakeSocket {
  constructor(url) {
    this.url = url;
    this.sent = [];
  }
  send(text) { this.sent.push(JSON.parse(text)); }
  close() {}
  receive(value) { this.onmessage({data: JSON.stringify(value)}); }
}

function networkFixture() {
  const original = {fetch: global.fetch, WebSocket: global.WebSocket, localStorage: global.localStorage};
  const fixture = {sockets: [], identities: 0, clients: []};
  global.localStorage = storage();
  global.fetch = async () => ({ok: true, json: async () => {
    const n = ++fixture.identities;
    return {playerId: `player-${n}`, token: `token-${n}`};
  }});
  global.WebSocket = class extends FakeSocket {
    constructor(url) { super(url); fixture.sockets.push(this); }
  };
  fixture.close = () => {
    fixture.clients.forEach(c => c.close());
    Object.assign(global, original);
  };
  return fixture;
}

class Element {
  constructor() {
    this.children = [];
    this.listeners = {};
    this.value = '';
    this.hidden = false;
    this.disabled = false;
    this.open = false;
    this.classList = {toggle() {}};
  }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  setAttribute() {}
  addEventListener(type, fn) { this.listeners[type] = fn; }
  click() { if (!this.disabled) this.listeners.click?.(); }
  showModal() { this.open = true; }
  close() { this.open = false; }
}

function pageEnvironment() {
  const html = fs.readFileSync(path.join(root, 'src/index.html'), 'utf8');
  const elements = Object.fromEntries([...html.matchAll(/\bid="([^"]+)"/g)]
    .map(([, id]) => [id, new Element()]));
  elements['score-plan'].value = 'low';
  const window = {addEventListener() {}};
  window.parent = window;
  const saved = storage();
  const context = {document: {getElementById: id => elements[id] || null,
    createElement: () => new Element(), body: new Element()}, window,
    localStorage: saved, sessionStorage: saved, location: {
      search: '', origin: 'http://localhost:8080', protocol: 'http:',
      hostname: 'localhost', pathname: '/', port: '8080'
    }, URL, URLSearchParams, crypto: require('node:crypto').webcrypto,
    console, navigator: {}, setTimeout: () => 1, clearTimeout() {}, setInterval() {}};
  return {elements, context};
}

async function pageFixture() {
  const fixture = pageEnvironment();
  class Transport {
    constructor() { fixture.transport = this; this.commands = []; this.isConnected = false; }
    async start() { this.isConnected = true; this.onReady(); }
    send(type, data = {}) { this.commands.push({type, data}); }
    create() {}
    join() {}
    close() {}
  }
  Object.assign(fixture.context, await shared(), {Transport});
  vm.runInNewContext(source.replace(/^import .*;\n/gm, ''), fixture.context);
  return fixture;
}

function lobby() {
  return {type: 'ACK', roomId: 'room-123', version: 3, playerId: 'a',
    players: ['a', 'b', 'c', 'd'], ready: [], online: {a: true, b: true, c: true, d: true}};
}

function playing() {
  return {...lobby(), version: 5, hand: [{id: 1}, {id: 52}], counts: [2, 13, 13, 13],
    scores: [0, 0, 0, 0], turn: 0, table: null, trick: 1, winner: -1,
    deadline: Date.now() + 30000, legal: [pair()], events: []};
}

function settled(version, gameSerial) {
  return {...playing(), version, gameSerial, winner: 0, counts: [0, 13, 13, 13],
    scores: [18, -6, -6, -6], hand: [], legal: [],
    events: [{type: 'SETTLED', version: 3, detail: '18,-6,-6,-6'}]};
}

function fourWindowHarness() {
  const elements = Object.fromEntries(['frames', 'test-status', 'all-ready', 'new-table']
    .map(id => [id, {append() {}, addEventListener(type, fn) { this[type] = fn; }}]));
  const frames = [];
  let receive;
  const context = {URLSearchParams, crypto: {randomUUID: () => 'session-test-123'},
    location: {search: '?test=session-test-123', origin: 'http://localhost:8080'},
    window: {addEventListener(type, fn) { receive = fn; }}, document: {
      getElementById: id => elements[id], createElement() {
        const frame = {contentWindow: {postMessage(data, origin) { frame.sent = {data, origin}; }}};
        frames.push(frame);
        return frame;
      }
    }};
  vm.runInNewContext(fs.readFileSync(path.join(root, 'src/test.js'), 'utf8'), context);
  return {elements, frames, receive, origin: context.location.origin};
}

module.exports = {root, source, shared, pair, single, networkFixture, pageFixture,
  lobby, playing, settled, fourWindowHarness};
