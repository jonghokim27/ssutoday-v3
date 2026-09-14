import assert from 'node:assert/strict';
import { test } from 'node:test';
import { registerIosAppAttest } from '../frontend/src/features/reservation/api/registerIosAppAttest.ts';

const KEY = Buffer.alloc(32, 1).toString('base64');
const NEXT_KEY = Buffer.alloc(32, 2).toString('base64');
const CHALLENGE = Buffer.alloc(32, 3).toString('base64url');
const PROOF = Buffer.from('fixture attestation').toString('base64');
const success = data => ({ ok: true, statusCode: 'SSU2000', data });
const denied = { ok: false, statusCode: 'SSU4206', message: 'rejected' };
const pending = { challenge: CHALLENGE, attestation: PROOF };

function scenario(overrides = {}) {
  const calls = [];
  const state = { studentId: 20260000, time: 0, key: { keyId: KEY, registered: false } };
  const deps = {
    getStudentId: async () => state.studentId,
    prepare: async id => { calls.push(['prepare', id]); return state.key; },
    challenge: async () => {
      calls.push('challenge');
      return success({ studentId: 20260000, purpose: 'APP_ATTEST_REGISTER', reservationId: null, challenge: CHALLENGE, expiresInSeconds: 60 });
    },
    attest: async (id, keyId, challenge) => { calls.push(['attest', id, keyId, challenge]); return { keyId, challenge, attestation: PROOF }; },
    register: async input => { calls.push(['register', input]); return success({ keyId: input.keyId }); },
    confirm: async (id, keyId) => { calls.push(['confirm', id, keyId]); },
    reset: async (id, keyId) => { calls.push(['reset', id, keyId]); state.key = { keyId: NEXT_KEY, registered: false }; },
    now: () => state.time,
    ...overrides,
  };
  return { calls, state, deps, run: () => registerIosAppAttest(20260000, deps) };
}

test('first iOS registration confirms the native key only after server acceptance', async () => {
  const s = scenario();
  assert.deepEqual(await s.run(), success(null));
  assert.deepEqual(s.calls, [
    ['prepare', 20260000], 'challenge', ['attest', 20260000, KEY, CHALLENGE],
    ['register', { keyId: KEY, ...pending }], ['confirm', 20260000, KEY],
  ]);
});

test('registered keys skip Apple registration and server round trips', async () => {
  const s = scenario();
  s.state.key.registered = true;
  assert.deepEqual(await s.run(), success(null));
  assert.deepEqual(s.calls, [['prepare', 20260000]]);
});

test('a lost registration response resends the exact persisted payload without a fresh challenge', async () => {
  const s = scenario();
  s.state.key.pending = pending;
  s.state.time = 120_000;
  assert.deepEqual(await s.run(), success(null));
  assert.deepEqual(s.calls, [['prepare', 20260000], ['register', { keyId: KEY, ...pending }], ['confirm', 20260000, KEY]]);
});

test('ambiguous network and server failures retain the pending key without confirming or resetting', async t => {
  for (const kind of ['network', 'server']) {
    await t.test(kind, async () => {
      const s = scenario({ register: async () => {
        if (kind === 'network') throw new Error('network failure');
        return { ok: false, statusCode: 'SSU5000', message: 'unavailable' };
      } });
      s.state.key.pending = pending;
      if (kind === 'network') await assert.rejects(s.run(), /network failure/);
      else assert.equal((await s.run()).statusCode, 'SSU5000');
      assert.deepEqual(s.calls, [['prepare', 20260000]]);
      assert.deepEqual(s.state.key.pending, pending);
    });
  }
});

test('definitive registration rejection resets only that account key and retries at most once', async t => {
  for (const retrySucceeds of [true, false]) {
    await t.test(String(retrySucceeds), async () => {
      const s = scenario();
      s.state.key.pending = pending;
      const register = s.deps.register;
      let requests = 0;
      s.deps.register = async input => {
        await register(input);
        return ++requests === 2 && retrySucceeds ? success({ keyId: input.keyId }) : denied;
      };
      assert.equal((await s.run()).ok, retrySucceeds);
      assert.equal(requests, 2);
      assert.deepEqual(s.calls.filter(c => c[0] === 'reset'), retrySucceeds
        ? [['reset', 20260000, KEY]]
        : [['reset', 20260000, KEY], ['reset', 20260000, NEXT_KEY]]);
      assert.deepEqual(s.calls.filter(c => c[0] === 'confirm'), retrySucceeds ? [['confirm', 20260000, NEXT_KEY]] : []);
    });
  }
});

test('native invalid-key errors allow one fresh key while outages never generate retry loops', async t => {
  for (const code of ['APP_ATTEST_KEY_INVALID', 'ATTESTATION_UNAVAILABLE']) {
    await t.test(code, async () => {
      const s = scenario();
      let attempts = 0;
      const attest = s.deps.attest;
      s.deps.attest = async (...args) => {
        attempts++;
        if (attempts === 1) {
          s.state.key = { keyId: NEXT_KEY, registered: false };
          throw Object.assign(new Error('provider'), { code });
        }
        return attest(...args);
      };
      if (code === 'APP_ATTEST_KEY_INVALID') {
        assert.equal((await s.run()).ok, true);
        assert.equal(attempts, 2);
      } else {
        await assert.rejects(s.run(), { code });
        assert.equal(attempts, 1);
      }
    });
  }
});

test('registration rejects mismatched challenge scope and expired challenges before calling Apple', async t => {
  for (const replacement of [{ studentId: 20260001 }, { purpose: 'VERIFY_PHOTO_UPLOAD' }, { reservationId: 42 },
    { challenge: CHALLENGE.slice(0, -1) + 'B' }, { expiresInSeconds: 0 }, { expiresInSeconds: 61 }, { expiresInSeconds: NaN }]) {
    await t.test(JSON.stringify(replacement), async () => {
      const s = scenario();
      const issue = s.deps.challenge;
      s.deps.challenge = async () => { const r = await issue(); return { ...r, data: { ...r.data, ...replacement } }; };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.deepEqual(s.calls, [['prepare', 20260000], 'challenge']);
    });
  }
  const s = scenario();
  const issue = s.deps.challenge;
  s.deps.challenge = async () => { s.state.time = 60_000; return issue(); };
  assert.equal((await s.run()).statusCode, 'SSU4206');
  assert.deepEqual(s.calls, [['prepare', 20260000], 'challenge']);
});

test('account switches across asynchronous boundaries never approve another account key', async t => {
  for (const phase of ['prepare', 'challenge', 'attest', 'register']) {
    await t.test(phase, async () => {
      const s = scenario();
      const original = s.deps[phase];
      s.deps[phase] = async (...args) => { const r = await original(...args); s.state.studentId++; return r; };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.calls.some(c => c[0] === 'confirm'), false);
      if (phase !== 'register') assert.equal(s.calls.some(c => c[0] === 'register'), false);
    });
  }
});

test('malformed native registration or a different server key cannot be confirmed', async t => {
  for (const change of [{ keyId: NEXT_KEY }, { challenge: 'invalid' }, { attestation: 'bad proof' }, { attestation: 'A'.repeat(65537) }]) {
    await t.test(Object.keys(change)[0], async () => {
      const s = scenario({ attest: async () => ({ keyId: KEY, ...pending, ...change }) });
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.calls.some(c => ['register', 'confirm'].includes(c[0])), false);
    });
  }
  const s = scenario({ register: async () => success({ keyId: NEXT_KEY }) });
  assert.equal((await s.run()).statusCode, 'SSU4206');
  assert.equal(s.calls.some(c => c[0] === 'confirm'), false);
});
