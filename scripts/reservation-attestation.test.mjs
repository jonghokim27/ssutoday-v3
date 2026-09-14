import assert from 'node:assert/strict';
import { test } from 'node:test';
import { requestReserveWithAttestation } from '../frontend/src/features/reservation/api/requestReserveWithAttestation.ts';

const CHALLENGE = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8';
const success = { ok: true, statusCode: 'SSU2000', data: { idx: 42 } };
function scenario(platform = 'android') {
  const state = { student: 20260000, time: 0, submitted: null, signed: null, prepared: false };
  const input = { turnstileToken: 'turnstile', roomNo: 1, date: '2026-09-14', startBlock: 20, endBlock: 23 };
  const deps = {
    platform: () => platform,
    getStudentId: async () => state.student,
    prepare: async () => { state.prepared = true; },
    registerIos: async () => { state.prepared = true; return success; },
    challenge: async () => ({ ...success, data: { studentId: 20260000, purpose: 'RESERVATION_CREATE', reservationId: null, challenge: CHALLENGE, expiresInSeconds: 60 } }),
    attest: async value => {
      assert.equal(state.prepared, true);
      state.signed = value;
      return platform === 'ios'
        ? { platform, keyId: Buffer.alloc(32, 1).toString('base64'), attestation: Buffer.from('assertion fixture').toString('base64') }
        : { platform, attestation: 'opaque-token' };
    },
    submit: async value => { state.submitted = value; return success; },
    now: () => state.time,
  };
  return { state, input, deps, run: () => requestReserveWithAttestation(input, deps) };
}

for (const platform of ['android', 'ios']) {
  test(`${platform} signs the authenticated reservation fields before submission`, async () => {
    const s = scenario(platform);
    assert.equal(await s.run(), success);
    assert.deepEqual(s.state.signed, { studentId: 20260000, roomNo: '1', date: '2026-09-14', startBlock: 20, endBlock: 23, challenge: CHALLENGE });
    assert.equal(s.state.submitted.platform, platform);
    assert.equal(s.state.submitted.challenge, CHALLENGE);
    assert.ok(s.state.submitted.attestation);
    assert.equal('keyId' in s.state.submitted, platform === 'ios');
  });
  test(`${platform} unsupported devices never submit and preserve the modal error`, async () => {
    for (const stage of [platform === 'ios' ? 'registerIos' : 'prepare', 'attest']) {
      const s = scenario(platform);
      const error = Object.assign(new Error('unsupported'), { code: 'ATTESTATION_UNSUPPORTED' });
      s.deps[stage] = async () => { throw error; };
      await assert.rejects(s.run(), value => value === error);
      assert.equal(s.state.submitted, null);
    }
  });
}

test('legacy apps keep the old reservation payload without invoking attestation', async () => {
  const s = scenario(null);
  for (const method of ['getStudentId', 'prepare', 'registerIos', 'challenge', 'attest']) s.deps[method] = () => assert.fail(method);
  assert.equal(await s.run(), success);
  assert.deepEqual(s.state.submitted, { ...s.input, roomNo: '1' });
});

test('caller mutation during signing cannot change the submitted reservation', async () => {
  const s = scenario();
  const attest = s.deps.attest;
  s.deps.attest = async value => { s.input.roomNo = 2; s.input.endBlock = 30; return attest(value); };
  await s.run();
  assert.equal(s.state.submitted.roomNo, '1');
  assert.equal(s.state.submitted.endBlock, 23);
});

test('wrong challenge scope, encoding and expiry fail before submission', async () => {
  for (const change of [{ studentId: 1 }, { purpose: 'VERIFY_PHOTO_UPLOAD' }, { reservationId: 42 }, { challenge: 'forged' }, { expiresInSeconds: 0 }, { expiresInSeconds: 61 }]) {
    const s = scenario();
    const challenge = s.deps.challenge;
    s.deps.challenge = async () => { const result = await challenge(); return { ...result, data: { ...result.data, ...change } }; };
    assert.equal((await s.run()).statusCode, 'SSU4206');
    assert.equal(s.state.submitted, null);
    assert.equal(s.state.signed, null);
  }
});

test('account changes and expired challenges are rejected at each async boundary', async () => {
  for (const stage of ['prepare', 'challenge', 'attest']) {
    for (const change of ['account', 'expiry']) {
      if (stage === 'prepare' && change === 'expiry') continue;
      const s = scenario();
      const original = s.deps[stage];
      s.deps[stage] = async (...args) => {
        const result = await original(...args);
        if (change === 'account') s.state.student++;
        else s.state.time = 60000;
        return result;
      };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.submitted, null);
    }
  }
});

test('provider outages defer the decision to server policy with partial evidence', async () => {
  for (const code of ['ATTESTATION_UNAVAILABLE', 'TIMEOUT', 'ATTESTATION_REJECTED', 'NATIVE_ERROR']) {
    const s = scenario();
    s.deps.attest = async () => { throw Object.assign(new Error('provider'), { code }); };
    const result = await s.run();
    if (['ATTESTATION_UNAVAILABLE', 'TIMEOUT'].includes(code)) {
      assert.equal(result, success);
      assert.equal(s.state.submitted.platform, 'android');
      assert.equal(s.state.submitted.challenge, CHALLENGE);
      assert.equal(s.state.submitted.attestation, undefined);
    } else {
      assert.equal(result.statusCode, 'SSU4206');
      assert.equal(s.state.submitted, null);
    }
  }
});

test('malformed native proofs never reach the reservation API', async () => {
  for (const proof of [null, { platform: 'ios', attestation: 'token' }, { platform: 'android', attestation: '' }, { platform: 'android', attestation: 'bad token' }, { platform: 'android', attestation: 'a'.repeat(32769) }, { platform: 'android', attestation: 'token', keyId: 'unexpected' }]) {
    const s = scenario();
    s.deps.attest = async () => proof;
    assert.equal((await s.run()).statusCode, 'SSU4206');
    assert.equal(s.state.submitted, null);
  }
});
