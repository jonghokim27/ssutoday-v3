import type { ApiResult } from '../../../shared/api/types';
import type { AttestPhotoResult, AttestReservationRequest } from '../../../shared/native/nativeBridge';

export type ReservationInput = { turnstileToken: string; roomNo: number | string; date: string; startBlock: number; endBlock: number };
export type ReservationSubmission = ReservationInput & { platform?: 'android' | 'ios'; challenge?: string; keyId?: string; attestation?: string };
export type ReservationChallenge = { studentId: number; purpose: 'RESERVATION_CREATE'; reservationId: null; challenge: string; expiresInSeconds: number };
export type ReservationAttestationDependencies = {
  platform(): 'android' | 'ios' | null;
  getStudentId(): Promise<number | null>;
  prepare(): Promise<void>;
  registerIos(studentId: number): Promise<ApiResult<null>>;
  challenge(): Promise<ApiResult<ReservationChallenge>>;
  attest(input: AttestReservationRequest): Promise<AttestPhotoResult>;
  submit(input: ReservationSubmission): Promise<ApiResult<{ idx: number }>>;
  now(): number;
};
const rejected = (): ApiResult<{ idx: number }> => ({ ok: false, statusCode: 'SSU4206', message: '예약 인증을 다시 시도해 주세요' });
const isOutage = (error: unknown) => ['ATTESTATION_UNAVAILABLE', 'TIMEOUT'].includes((error as { code?: string })?.code ?? '');

export async function requestReserveWithAttestation(input: ReservationInput, deps: ReservationAttestationDependencies): Promise<ApiResult<{ idx: number }>> {
  const request: ReservationSubmission = { ...input, roomNo: String(input.roomNo) };
  const platform = deps.platform();
  if (!platform) return deps.submit(request);
  const studentId = await deps.getStudentId();
  if (!Number.isSafeInteger(studentId) || Number(studentId) <= 0 || Number(studentId) > 2147483647) return rejected();
  try {
    if (platform === 'ios') {
      const result = await deps.registerIos(studentId!);
      if (!result.ok) return result;
    } else {
      await deps.prepare();
    }
  } catch (error) {
    if ((error as { code?: string })?.code === 'ATTESTATION_UNSUPPORTED') throw error;
    if (!isOutage(error)) return rejected();
  }
  if (await deps.getStudentId() !== studentId) return rejected();
  const started = deps.now();
  const response = await deps.challenge();
  if (!response.ok) return response;
  const challenge = response.data;
  if (!challenge || challenge.studentId !== studentId || challenge.purpose !== 'RESERVATION_CREATE' || challenge.reservationId !== null ||
      !/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(challenge.challenge) || !Number.isFinite(challenge.expiresInSeconds) ||
      challenge.expiresInSeconds <= 0 || challenge.expiresInSeconds > 60) return rejected();
  const deadline = started + challenge.expiresInSeconds * 1000;
  if (deps.now() >= deadline || await deps.getStudentId() !== studentId) return rejected();
  request.platform = platform;
  request.challenge = challenge.challenge;
  try {
    const proof = await deps.attest({ studentId: studentId!, roomNo: String(request.roomNo), date: request.date,
      startBlock: request.startBlock, endBlock: request.endBlock, challenge: challenge.challenge });
    if (!proof || proof.platform !== platform || typeof proof.attestation !== 'string' || !proof.attestation ||
        proof.attestation.length > 32768 || /\s/.test(proof.attestation)) return rejected();
    if (platform === 'ios') {
      if (typeof proof.keyId !== 'string' || !/^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(proof.keyId) ||
          !/^[A-Za-z0-9+/]+={0,2}$/.test(proof.attestation)) return rejected();
      request.keyId = proof.keyId;
    } else if (proof.keyId !== undefined) return rejected();
    request.attestation = proof.attestation;
  } catch (error) {
    if ((error as { code?: string })?.code === 'ATTESTATION_UNSUPPORTED') throw error;
    if (!isOutage(error)) return rejected();
  }
  if (deps.now() >= deadline || await deps.getStudentId() !== studentId) return rejected();
  return deps.submit(request);
}
