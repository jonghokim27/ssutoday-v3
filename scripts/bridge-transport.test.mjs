import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import vm from 'node:vm';

const require = createRequire(import.meta.url);
const ts = require('../frontend/node_modules/typescript');

function transport() {
  const sent = [];
  const listeners = new Map();
  const timers = new Map();
  let nextId = 0;
  const window = {
    ReactNativeWebView: { postMessage: raw => sent.push(JSON.parse(raw)) },
    addEventListener: (name, handler) => listeners.set(name, handler),
  };
  const document = { addEventListener() {} };
  const globals = {
    window, document, crypto: { randomUUID: () => String(++nextId) },
    setTimeout: callback => { const id = ++nextId; timers.set(id, callback); return id; },
    clearTimeout: id => timers.delete(id),
  };
  function load(file, dependency) {
    const source = readFileSync(new URL(`../frontend/src/shared/native/${file}.ts`, import.meta.url), 'utf8');
    const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
    const exports = {};
    vm.runInNewContext(outputText, { ...globals, exports, require: () => dependency });
    return exports;
  }
  const api = load('bridgeTransport', load('bridgeProtocol'));
  const message = (envelope, source = null) => listeners.get('message')({ data: JSON.stringify(envelope), source });
  const handshake = (capabilities, source) => message({ v: 1, kind: 'handshake', id: 'ready', platform: 'android', appVersion: '3.0.2', protocolVersion: 1, capabilities }, source);
  return { api, sent, timers, message, handshake };
}

test('requests wait for handshake before capability validation and dispatch', async () => {
  const t = transport();
  const request = t.api.request('security.attest', { captureId: 'camera' });
  assert.equal(t.sent.length, 0);
  t.handshake(['security.attest']);
  await Promise.resolve();
  assert.equal(t.sent.length, 1);
  assert.deepEqual(t.sent[0].params, { captureId: 'camera' });
  t.message({ v: 1, kind: 'response', id: t.sent[0].id, ok: true, result: 'proof' });
  assert.equal(await request, 'proof');
  assert.equal(t.timers.size, 0);
});

test('an old app handshake rejects unsupported methods without dispatch', async () => {
  const t = transport();
  const result = t.api.request('security.attest');
  t.handshake(['device.getInfo']);
  await assert.rejects(result, error => error.code === 'UNSUPPORTED_METHOD');
  assert.equal(t.sent.length, 0);
  assert.equal(t.timers.size, 0);
});

test('even an unlimited camera request has a bounded handshake wait and cannot dispatch later', async () => {
  const t = transport();
  const result = t.api.request('camera.captureVerifyPhoto', undefined, 0);
  const failure = assert.rejects(result, error => error.code === 'TIMEOUT');
  for (const callback of t.timers.values()) callback();
  t.timers.clear();
  await failure;
  t.handshake(['camera.captureVerifyPhoto']);
  await Promise.resolve();
  assert.equal(t.sent.length, 0);
});

test('foreign frame messages cannot forge a handshake or a native result', async () => {
  const t = transport();
  const result = t.api.request('security.attest');
  t.handshake(['security.attest'], {});
  await Promise.resolve();
  assert.equal(t.sent.length, 0);
  assert.equal(t.api.hasCapability('security.attest'), false);
  t.handshake(['security.attest']);
  await Promise.resolve();
  const response = { v: 1, kind: 'response', id: t.sent[0].id, ok: true, result: 'real' };
  t.message({ ...response, result: 'forged' }, {});
  assert.equal(t.timers.size, 1);
  t.message(response);
  assert.equal(await result, 'real');
});
