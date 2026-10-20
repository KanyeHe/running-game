const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {root, source, shared, pair, single, networkFixture, pageFixture,
  lobby, playing, settled, fourWindowHarness} = require('./fixtures.cjs');

test('H5 page contains every DOM control used by the application', () => {
  const html = fs.readFileSync(path.join(root, 'src/index.html'), 'utf8');
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  assert.equal(ids.length, new Set(ids).size, 'unique DOM ids');
  for (const [, id] of source.matchAll(/\$\('([^']+)'\)/g)) assert.ok(ids.includes(id), `missing ${id}`);
  assert.ok(!html.includes('http://fonts'), 'no external font dependency');
  assert.ok(html.includes('type="module"'));
});

test('static artifacts share source and have browser-resolvable imports', () => {
  for (const file of ['model', 'Selection', 'Transport']) {
    const output = fs.readFileSync(path.join(root, 'dist/shared', file + '.js'), 'utf8');
    for (const [, specifier] of output.matchAll(/from\s+['"]([^'"]+)['"]/g))
      assert.ok(specifier.endsWith('.js'), specifier);
    assert.equal(output, fs.readFileSync(path.join(root,
      '../server/src/main/resources/static/shared', file + '.js'), 'utf8'));
  }
  for (const file of ['index.html', 'app.css', 'app.js', 'test.html', 'test.js'])
    assert.equal(fs.readFileSync(path.join(root, 'src', file), 'utf8'),
      fs.readFileSync(path.join(root, 'dist', file), 'utf8'));
});

test('selection keeps physical IDs and loops every server candidate', async () => {
  const {Selection, combinationKey} = await shared();
  const selection = new Selection();
  const a = pair(), b = single();
  selection.reset([a, b]);
  selection.toggle(1);
  selection.toggle(8);
  assert.deepEqual([...selection.selected], [1]);
  assert.equal(selection.exact(), undefined);
  selection.toggle(52);
  assert.equal(selection.exact(), a);
  assert.equal(combinationKey(a), 'PAIR:[1, 52]:{52=2}:15:1:2');
  assert.equal(selection.hint(), a);
  assert.equal(selection.hint(), b);
  assert.equal(selection.hint(), a);
});

async function enterProfile(fixture, Transport, i) {
  const client = new Transport('http://localhost:8080', `profile-${i}`);
  fixture.clients.push(client);
  let ready = 0;
  client.onReady = () => ready++;
  await client.start(`玩家${i}`);
  const socket = fixture.sockets.at(-1);
  socket.onopen();
  assert.equal(socket.sent[0].token, `token-${i + 1}`);
  socket.receive({type: 'LOGIN_SUCCESS'});
  assert.equal(ready, 1);
  assert.equal(client.isConnected, true);
  if (i === 0) client.create('test-room'); else client.join('test-room');
  socket.receive({type: 'ACK', requestId: socket.sent.at(-1).requestId,
    roomId: 'test-room', version: 2 + i, playerId: `player-${i + 1}`});
}

async function restoreProfile(fixture, Transport) {
  const client = new Transport('http://localhost:8080', 'profile-2');
  fixture.clients.push(client);
  await client.start('恢复玩家');
  const socket = fixture.sockets.at(-1);
  socket.onopen();
  assert.equal(socket.sent[0].token, 'token-3');
  socket.receive({type: 'LOGIN_SUCCESS'});
  assert.equal(socket.sent[1].type, 'RECONNECT');
  assert.equal(socket.sent[1].roomId, 'test-room');
  assert.equal(fixture.identities, 4, 'refresh must not create another identity');
  let error;
  client.onError = code => { error = code; };
  socket.receive({type: 'ERROR', code: 'STALE_VERSION', requestId: socket.sent[1].requestId});
  assert.equal(error, 'STALE_VERSION');
  assert.equal(socket.sent.at(-1).type, 'SNAPSHOT');
}

test('four profiles authenticate independently and restore their room and sequence', async () => {
  const {Transport} = await shared();
  const fixture = networkFixture();
  try {
    for (let i = 0; i < 4; i++) await enterProfile(fixture, Transport, i);
    assert.equal(new Set(fixture.sockets.map(s => s.sent[0].token)).size, 4);
    await restoreProfile(fixture, Transport);
  } finally { fixture.close(); }
});

test('four-window profiles are isolated and only trusted frames can update readiness', () => {
  const {elements, frames, receive, origin} = fourWindowHarness();
  const queries = frames.map(frame => new URL(frame.src, origin).searchParams);
  assert.equal(new Set(queries.map(q => q.get('profile'))).size, 4);
  assert.equal(queries[0].get('auto'), 'create');
  assert.ok(queries.slice(1).every(q => q.get('auto') === 'join'));
  assert.equal(new Set(queries.map(q => q.get('room'))).size, 1);
  const data = {type: 'LUZHOU_TEST_STATE', players: 4, ready: 0, inGame: false};
  receive({origin: 'http://other', source: frames[0].contentWindow, data});
  receive({origin, source: {}, data});
  assert.equal(elements['test-status'].textContent, undefined);
  frames.forEach((frame, i) => receive({origin, source: frame.contentWindow,
    data: {...data, profile: queries[i].get('profile')}}));
  assert.equal(elements['all-ready'].disabled, false);
  elements['all-ready'].click();
  assert.ok(frames.every(frame => frame.sent.origin === origin
    && frame.sent.data.type === 'LUZHOU_TEST_READY'));
});

test('stale READY is retried after an authoritative snapshot', async () => {
  const {elements, transport} = await pageFixture();
  assert.equal(elements['create-button'].disabled, false);
  transport.onSnapshot(lobby());
  elements['ready-button'].click();
  assert.equal(transport.commands.at(-1).type, 'READY');
  const beforeRetry = transport.commands.length;
  transport.onError('STALE_VERSION');
  transport.onSnapshot({...lobby(), type: 'SNAPSHOT', version: 4, ready: ['b']});
  assert.equal(transport.commands.length, beforeRetry + 1);
  assert.equal(transport.commands.at(-1).type, 'READY');
});

test('page selects the exact server candidate and disables play after turn changes', async () => {
  const {elements, transport} = await pageFixture();
  const {combinationKey} = await shared();
  transport.onSnapshot(playing());
  assert.equal(elements['room-number'].textContent, 'room-123');
  assert.equal(elements['room-form'].hidden, true);
  assert.equal(elements.hand.children.length, 2);
  assert.equal(elements['play-button'].disabled, true);
  elements['hint-button'].click();
  assert.equal(elements['play-button'].disabled, false);
  elements['play-button'].click();
  assert.equal(transport.commands.at(-1).type, 'PLAY');
  assert.equal(transport.commands.at(-1).data.combinationKey, combinationKey(pair()));
  transport.onSnapshot({...playing(), version: 6, turn: 1, legal: []});
  assert.equal(elements['play-button'].disabled, true);
  assert.equal(elements['hint-button'].disabled, true);
});

test('settlement appears again for a later game with identical results', async () => {
  const {elements, transport} = await pageFixture();
  transport.onSnapshot(settled(7, 1));
  assert.equal(elements.settlement.open, true);
  assert.equal(elements['settlement-scores'].children.length, 4);
  elements['next-round'].click();
  assert.equal(transport.commands.at(-1).type, 'READY');
  assert.equal(elements.settlement.open, false);
  transport.onSnapshot({...playing(), version: 8, gameSerial: 2});
  transport.onSnapshot(settled(9, 2));
  assert.equal(elements.settlement.open, true);
});
