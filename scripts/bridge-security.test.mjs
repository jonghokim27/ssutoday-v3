import assert from 'node:assert/strict';
import { test } from 'node:test';
import vm from 'node:vm';
import { canDispatchBridge, isAttestParams, isReservationAttestParams, isAppAttestKey, isTrustedBridgeUrl, secureBridgeScript } from '../mobile/src/bridge/bridgeSecurity.ts';

const ORIGIN = 'https://v3.ssu.today';

test('reservation signing accepts structured fields and rejects forged or unbounded input', () => {
  const valid = { studentId: 20260000, roomNo: '1', date: '2026-09-14', startBlock: 20, endBlock: 23, challenge: 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8' };
  assert.equal(isReservationAttestParams(valid), true);
  for (const value of [null, [], {}, { ...valid, studentId: 0 }, { ...valid, studentId: 2147483648 }, { ...valid, roomNo: '' }, { ...valid, roomNo: 'r'.repeat(101) }, { ...valid, date: '2026-9-14' }, { ...valid, startBlock: 11 }, { ...valid, endBlock: 44 }, { ...valid, endBlock: 19 }, { ...valid, challenge: 'client-hash' }]) {
    assert.equal(isReservationAttestParams(value), false);
  }
});

test('App Attest key operations require an authenticated student scope and canonical key encoding', () => {
  const valid = { studentId: 20260000, keyId: Buffer.alloc(32, 1).toString('base64') };
  assert.equal(isAppAttestKey(valid), true);
  for (const input of [null, undefined, [], {}, { ...valid, studentId: 0 }, { ...valid, studentId: 2147483648 },
    { ...valid, keyId: valid.keyId.slice(0, -1) }, { ...valid, keyId: valid.keyId.slice(0, -2) + 'B=' }]) {
    assert.equal(isAppAttestKey(input), false);
  }
});

test('bridge requires the exact HTTPS origin, a ready document and the native token', () => {
  assert.equal(canDispatchBridge(`${ORIGIN}/reservations`, ORIGIN, true, 'secret', 'secret'), true);
  for (const url of ['http://v3.ssu.today', `${ORIGIN}.evil.test`, 'https://evil.v3.ssu.today', `${ORIGIN}:444`, 'https://user@v3.ssu.today', 'about:blank', 'invalid']) {
    assert.equal(isTrustedBridgeUrl(url), false, url);
    assert.equal(canDispatchBridge(url, ORIGIN, true, 'secret', 'secret'), false);
    assert.equal(canDispatchBridge(ORIGIN, url, true, 'secret', 'secret'), false);
  }
  assert.equal(canDispatchBridge(ORIGIN, ORIGIN, false, 'secret', 'secret'), false);
  assert.equal(canDispatchBridge(ORIGIN, ORIGIN, true, undefined, 'secret'), false);
  assert.equal(canDispatchBridge(ORIGIN, ORIGIN, true, 'forged', 'secret'), false);
});

function frame(origin, mainFrame) {
  const sent = [];
  const window = { ReactNativeWebView: { postMessage: raw => sent.push(JSON.parse(raw)) } };
  window.top = mainFrame ? window : {};
  const context = vm.createContext({ window, location: { origin } });
  return { window, context, sent };
}

test('main frame bootstrap attaches a private token and preserves request fields', () => {
  const f = frame(ORIGIN, true);
  const script = secureBridgeScript('native-secret');
  vm.runInContext(script, f.context);
  vm.runInContext(script, f.context);
  f.window.ReactNativeWebView.postMessage(JSON.stringify({ kind: 'request', id: '1', params: { captureId: 'camera' }, bridgeToken: 'forged' }));
  assert.deepEqual(f.sent, [{ kind: 'request', id: '1', params: { captureId: 'camera' }, bridgeToken: 'native-secret' }]);
  f.window.ReactNativeWebView.postMessage('not JSON');
  f.window.ReactNativeWebView.postMessage('[]');
  assert.equal(f.sent.length, 1);
  assert.equal(f.window.ReactNativeWebView.bridgeToken, undefined);
});

test('bootstrap never gives the token to a foreign origin or a subframe', () => {
  for (const [origin, main] of [[ORIGIN, false], ['https://smartid.ssu.ac.kr', true], ['https://challenges.cloudflare.com', false]]) {
    const f = frame(origin, main);
    vm.runInContext(secureBridgeScript('native-secret'), f.context);
    f.window.ReactNativeWebView.postMessage('{"kind":"request"}');
    assert.equal(f.sent[0].bridgeToken, undefined);
    assert.equal(canDispatchBridge(ORIGIN, ORIGIN, true, f.sent[0].bridgeToken, 'native-secret'), false);
  }
});

test('bootstrap wraps a native host object without redefining its methods', () => {
  const f = frame(ORIGIN, true);
  const original = f.window.ReactNativeWebView;
  original.injectedObjectJson = () => '{"test":true}';
  Object.freeze(original);
  vm.runInContext(secureBridgeScript('native-secret'), f.context);
  f.window.ReactNativeWebView.postMessage('{"id":"1"}');
  assert.equal(f.sent[0].bridgeToken, 'native-secret');
  assert.equal(f.window.ReactNativeWebView.injectedObjectJson(), '{"test":true}');
  assert.notEqual(f.window.ReactNativeWebView, original);
});

test('attestation accepts only scoped capture handles and canonical challenges', () => {
  const input = { captureId: '095f7fde-f301-4441-a1e5-0395712c3d66', studentId: 20260000, reservationId: 42, challenge: 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8' };
  assert.equal(isAttestParams(input), true);
  for (const replacement of [{ captureId: 'file:///cache/old.jpg' }, { studentId: 0 }, { studentId: 2147483648 }, { reservationId: 1.5 }, { challenge: input.challenge.slice(0, -1) + '9' }]) {
    assert.equal(isAttestParams({ ...input, ...replacement }), false);
  }
});
