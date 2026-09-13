import assert from 'node:assert/strict';
import { test } from 'node:test';
import { uploadVerifyPhotoWithAttestation } from '../frontend/src/features/reservation/api/uploadVerifyPhoto.ts';

const CHALLENGE = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8';
const PHOTO = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 0xff, 0xd9]);
const CAPTURE = '095f7fde-f301-4441-a1e5-0395712c3d66';
const success = { ok: true, statusCode: 'SSU2000', data: null };

function scenario(overrides = {}) {
  const calls = [];
  const state = { studentId: 20260000, time: 0, form: null };
  const deps = {
    attestationPlatform: () => 'android',
    getStudentId: async () => state.studentId,
    prepare: async () => { calls.push('prepare'); },
    registerIos: async () => { calls.push('register-ios'); return success; },
    capture: async scope => {
      calls.push(['capture', scope]);
      return { captureId: CAPTURE, uri: `data:image/jpeg;base64,${PHOTO.toString('base64')}`, name: 'capture.jpg', type: 'image/jpeg' };
    },
    turnstile: async () => { calls.push('turnstile'); return 'turnstile-token'; },
    challenge: async reservationId => {
      calls.push('challenge');
      return { ...success, data: { challenge: CHALLENGE, studentId: 20260000, reservationId, purpose: 'VERIFY_PHOTO_UPLOAD', expiresInSeconds: 60 } };
    },
    attest: async input => { calls.push(['attest', input]); return { platform: 'android', attestation: 'integrity-token' }; },
    release: async id => { calls.push(['release', id]); },
    upload: async form => { calls.push('upload'); state.form = form; return success; },
    now: () => state.time,
    ...overrides,
  };
  return { deps, calls, state, run: () => uploadVerifyPhotoWithAttestation(42, deps) };
}

test('camera bytes and native proof are uploaded with the authenticated challenge in order', async () => {
  const { run, calls, state } = scenario();
  assert.equal(await run(), success);
  assert.deepEqual(calls, [
    'prepare', ['capture', { studentId: 20260000, reservationId: 42 }], 'turnstile', 'challenge',
    ['attest', { captureId: CAPTURE, studentId: 20260000, reservationId: 42, challenge: CHALLENGE }],
    'upload', ['release', CAPTURE],
  ]);
  assert.deepEqual([...state.form.keys()], ['idx', 'file', 'turnstileToken', 'platform', 'challenge', 'attestation']);
  assert.deepEqual(Buffer.from(await state.form.get('file').arrayBuffer()), PHOTO);
  assert.equal(state.form.get('idx'), '42');
  assert.equal(state.form.get('platform'), 'android');
  assert.equal(state.form.get('challenge'), CHALLENGE);
  assert.equal(state.form.get('attestation'), 'integrity-token');
});

test('old apps without the capability keep the existing upload contract', async () => {
  const { run, calls, state } = scenario({ attestationPlatform: () => null });
  assert.equal(await run(), success);
  assert.deepEqual(calls, [['capture', undefined], 'turnstile', 'upload', ['release', CAPTURE]]);
  assert.deepEqual([...state.form.keys()], ['idx', 'file', 'turnstileToken']);
});

test('camera cancellation never requests a challenge or uploads', async () => {
  const { run, calls } = scenario({ capture: async () => null });
  assert.equal((await run()).statusCode, 'SSU0000');
  assert.deepEqual(calls, ['prepare']);
});

test('a new app cannot submit a photo without a native capture handle', async () => {
  const { run, state } = scenario({ capture: async () => ({ uri: '', name: 'bad.jpg', type: 'image/jpeg' }) });
  assert.equal((await run()).statusCode, 'SSU4206');
  assert.equal(state.form, null);
});

test('challenge scope and canonical encoding must match the photo operation', async t => {
  for (const replacement of [
    { studentId: 20260001 }, { reservationId: 43 }, { purpose: 'REGISTER_KEY' },
    { challenge: `${CHALLENGE.slice(0, -1)}9` }, { expiresInSeconds: 0 }, { expiresInSeconds: 61 },
  ]) {
    await t.test(JSON.stringify(replacement), async () => {
      const s = scenario();
      const issue = s.deps.challenge;
      s.deps.challenge = async id => {
        const result = await issue(id);
        return { ...result, data: { ...result.data, ...replacement } };
      };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.form, null);
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
      assert.equal(s.calls.some(call => Array.isArray(call) && call[0] === 'attest'), false);
    });
  }
});

test('account changes during Turnstile or attestation invalidate the upload', async t => {
  for (const phase of ['turnstile', 'attest']) {
    await t.test(phase, async () => {
      const s = scenario();
      const original = s.deps[phase];
      s.deps[phase] = async (...args) => { const result = await original(...args); s.state.studentId++; return result; };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.form, null);
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    });
  }
});

test('challenge lifetime includes challenge request and provider latency', async t => {
  for (const phase of ['challenge', 'attest']) {
    await t.test(phase, async () => {
      const s = scenario();
      const original = s.deps[phase];
      s.deps[phase] = async (...args) => { const result = await original(...args); s.state.time = 60_000; return result; };
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.form, null);
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    });
  }
});

test('only provider outages use partial evidence for the server observation policy', async t => {
  for (const code of ['ATTESTATION_UNAVAILABLE', 'TIMEOUT', 'ATTESTATION_REJECTED', 'INVALID_PARAMS', 'NATIVE_ERROR']) {
    await t.test(code, async () => {
      const s = scenario({ attest: async () => { throw Object.assign(new Error('test'), { code }); } });
      const result = await s.run();
      if (code === 'ATTESTATION_UNAVAILABLE' || code === 'TIMEOUT') {
        assert.equal(result, success);
        assert.equal(s.state.form.get('platform'), 'android');
        assert.equal(s.state.form.get('challenge'), CHALLENGE);
        assert.equal(s.state.form.has('attestation'), false);
      } else {
        assert.equal(result.statusCode, 'SSU4206');
        assert.equal(s.state.form, null);
      }
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    });
  }
});

test('malformed native proofs fail before upload', async t => {
  for (const proof of [{ platform: 'ios', attestation: 'token' }, { platform: 'android', attestation: '' }, { platform: 'android', attestation: 'bad token' }]) {
    await t.test(JSON.stringify(proof), async () => {
      const s = scenario({ attest: async () => proof });
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.form, null);
    });
  }
});

test('failed Turnstile, challenge and upload all release the capture', async t => {
  for (const phase of ['turnstile', 'challenge', 'upload']) {
    await t.test(phase, async () => {
      const s = scenario({ [phase]: async () => { throw new Error('network failure'); } });
      await assert.rejects(s.run(), /network failure/);
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    });
  }
  const s = scenario({ challenge: async () => ({ ok: false, statusCode: 'SSU4205', message: 'rejected' }) });
  assert.equal((await s.run()).statusCode, 'SSU4205');
  assert.equal(s.state.form, null);
  assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
});

const IOS_KEY = Buffer.alloc(32, 1).toString('base64');

test('unsupported devices preserve the modal error and never upload', async () => {
  for (const platform of ['android', 'ios']) {
    for (const phase of [platform === 'android' ? 'prepare' : 'registerIos', 'attest']) {
      const error = Object.assign(new Error('unsupported'), { code: 'ATTESTATION_UNSUPPORTED' });
      const s = scenario({ attestationPlatform: () => platform, [phase]: async () => { throw error; } });
      await assert.rejects(s.run(), value => value === error);
      assert.equal(s.state.form, null);
      if (phase === 'attest') assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    }
  }
});
const IOS_PROOF = { platform: 'ios', keyId: IOS_KEY, attestation: Buffer.from('assertion fixture').toString('base64') };

test('iOS registers before capture and uploads its key and assertion with the captured bytes', async () => {
  const s = scenario({ attestationPlatform: () => 'ios', attest: async () => IOS_PROOF });
  assert.equal(await s.run(), success);
  assert.deepEqual(s.calls.slice(0, 4), ['register-ios', ['capture', { studentId: 20260000, reservationId: 42 }], 'turnstile', 'challenge']);
  assert.equal(s.state.form.get('platform'), 'ios');
  assert.equal(s.state.form.get('keyId'), IOS_KEY);
  assert.equal(s.state.form.get('attestation'), IOS_PROOF.attestation);
  assert.deepEqual(Buffer.from(await s.state.form.get('file').arrayBuffer()), PHOTO);
  assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
});

test('iOS registration rejection or account switching stops before opening the camera', async () => {
  const s = scenario({ attestationPlatform: () => 'ios', registerIos: async () => ({ ok: false, statusCode: 'SSU4206' }) });
  assert.equal((await s.run()).statusCode, 'SSU4206');
  assert.deepEqual(s.calls, []);
  s.deps.registerIos = async () => { s.state.studentId++; return success; };
  assert.equal((await s.run()).statusCode, 'SSU4206');
  assert.deepEqual(s.calls, []);
});

test('iOS provider unavailability preserves the server observation policy', async () => {
  const unavailable = async () => { throw Object.assign(new Error('unavailable'), { code: 'ATTESTATION_UNAVAILABLE' }); };
  const s = scenario({ attestationPlatform: () => 'ios', registerIos: unavailable, attest: unavailable });
  assert.equal(await s.run(), success);
  assert.equal(s.state.form.get('platform'), 'ios');
  assert.equal(s.state.form.get('challenge'), CHALLENGE);
  assert.equal(s.state.form.has('attestation'), false);
  assert.equal(s.state.form.has('keyId'), false);
});

test('iOS cannot upload assertions with missing, malformed or foreign-platform keys', async t => {
  for (const proof of [{ ...IOS_PROOF, keyId: undefined }, { ...IOS_PROOF, keyId: 'invalid' },
    { ...IOS_PROOF, platform: 'android' }, { ...IOS_PROOF, attestation: 'bad-proof' }]) {
    await t.test(JSON.stringify(proof), async () => {
      const s = scenario({ attestationPlatform: () => 'ios', attest: async () => proof });
      assert.equal((await s.run()).statusCode, 'SSU4206');
      assert.equal(s.state.form, null);
      assert.deepEqual(s.calls.at(-1), ['release', CAPTURE]);
    });
  }
});
