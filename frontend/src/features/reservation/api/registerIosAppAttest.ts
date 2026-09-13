import type { ApiResult } from '../../../shared/api/types';
import type { AppAttestKeyState, AppAttestRegistration } from '../../../shared/native/nativeBridge';

export type RegistrationChallenge = {
  studentId: number;
  purpose: 'APP_ATTEST_REGISTER';
  reservationId: null;
  challenge: string;
  expiresInSeconds: number;
};

export type IosRegistrationDependencies = {
  getStudentId(): Promise<number | null>;
  prepare(studentId: number): Promise<AppAttestKeyState>;
  challenge(): Promise<ApiResult<RegistrationChallenge>>;
  attest(studentId: number, keyId: string, challenge: string): Promise<AppAttestRegistration>;
  register(input: AppAttestRegistration): Promise<ApiResult<{ keyId: string }>>;
  confirm(studentId: number, keyId: string): Promise<void>;
  reset(studentId: number, keyId: string): Promise<void>;
  now(): number;
};

const validKey = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(value);
const validChallenge = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(value);
const rejected = (): ApiResult<null> => ({ ok: false, statusCode: 'SSU4206', message: '앱 인증을 다시 시도해 주세요' });

/** 서버 승인 전에는 native registered 상태를 설정하지 않는다. 유실된 응답은 같은 pending payload로 복구한다. */
export async function registerIosAppAttest(studentId: number, deps: IosRegistrationDependencies): Promise<ApiResult<null>> {
  for (let attempt = 0; attempt < 2; attempt++) {
    if (await deps.getStudentId() !== studentId) return rejected();
    const state = await deps.prepare(studentId);
    if (!state || !validKey(state.keyId) || typeof state.registered !== 'boolean') return rejected();
    if (await deps.getStudentId() !== studentId) return rejected();
    if (state.registered) return { ok: true, statusCode: 'SSU2000', data: null };
    let registration: AppAttestRegistration;
    if (state.pending) {
      registration = { keyId: state.keyId, ...state.pending };
    } else {
      const started = deps.now();
      const result = await deps.challenge();
      if (!result.ok) return result;
      const scope = result.data;
      if (!scope || scope.studentId !== studentId || scope.purpose !== 'APP_ATTEST_REGISTER' || scope.reservationId !== null ||
          !validChallenge(scope.challenge) || !Number.isFinite(scope.expiresInSeconds) || scope.expiresInSeconds <= 0 || scope.expiresInSeconds > 60) return rejected();
      if (deps.now() >= started + scope.expiresInSeconds * 1000 || await deps.getStudentId() !== studentId) return rejected();
      try {
        registration = await deps.attest(studentId, state.keyId, scope.challenge);
      } catch (error) {
        if ((error as { code?: string })?.code === 'APP_ATTEST_KEY_INVALID' && attempt === 0) continue;
        throw error;
      }
      if (registration?.challenge !== scope.challenge) return rejected();
      // 늦어진 attestation도 pending 상태로 남긴다. 서버가 이미 등록한 payload의 재전송 여부를 판단한다.
    }
    if (!registration || registration.keyId !== state.keyId || !validChallenge(registration.challenge) ||
        typeof registration.attestation !== 'string' || registration.attestation.length < 4 || registration.attestation.length > 65536 ||
        !/^[A-Za-z0-9+/]+={0,2}$/.test(registration.attestation)) return rejected();
    if (await deps.getStudentId() !== studentId) return rejected();
    const result = await deps.register(registration);
    if (!result.ok) {
      if (result.statusCode === 'SSU4206') {
        await deps.reset(studentId, state.keyId);
        if (attempt === 0) continue;
      }
      return result;
    }
    if (result.data?.keyId !== state.keyId || await deps.getStudentId() !== studentId) return rejected();
    await deps.confirm(studentId, state.keyId);
    return { ok: true, statusCode: 'SSU2000', data: null };
  }
  return rejected();
}
