import type { ApiResult } from '../../../shared/api/types';
import type { AttestPhotoRequest, AttestPhotoResult, CapturePhotoScope, CapturedPhoto } from '../../../shared/native/nativeBridge';

export type PhotoChallenge = {
  challenge: string;
  expiresInSeconds: number;
  studentId: number;
  purpose: 'VERIFY_PHOTO_UPLOAD';
  reservationId: number;
};

export type PhotoUploadDependencies = {
  attestationPlatform(): 'android' | 'ios' | null;
  getStudentId(): Promise<number | null>;
  prepare(): Promise<void>;
  registerIos(studentId: number): Promise<ApiResult<null>>;
  capture(scope?: CapturePhotoScope): Promise<CapturedPhoto | null>;
  turnstile(): Promise<string>;
  challenge(reservationId: number): Promise<ApiResult<PhotoChallenge>>;
  attest(input: AttestPhotoRequest): Promise<AttestPhotoResult>;
  release(captureId: string): Promise<void>;
  upload(form: FormData): Promise<ApiResult<null>>;
  now(): number;
};

function photoBlob(photo: CapturedPhoto): Blob {
  if (photo.blob) return photo.blob;
  const match = /^data:image\/jpeg;base64,([A-Za-z0-9+/]+={0,2})$/.exec(photo.uri);
  if (!match) throw new Error('Invalid camera photo');
  return new Blob([Uint8Array.from(atob(match[1]), c => c.charCodeAt(0))], { type: 'image/jpeg' });
}

const rejected = (): ApiResult<null> => ({ ok: false, statusCode: 'SSU4206', message: '사진을 다시 촬영해 주세요' });

/** 등록 준비 → capture → Turnstile → challenge → 증명 → 업로드. 구버전 호환 여부는 capability로 결정한다. */
export async function uploadVerifyPhotoWithAttestation(reservationId: number, deps: PhotoUploadDependencies): Promise<ApiResult<null>> {
  const platform = deps.attestationPlatform();
  const supported = platform !== null;
  const studentId = supported ? await deps.getStudentId() : null;
  if (supported && (!Number.isSafeInteger(studentId) || Number(studentId) <= 0)) return rejected();
  if (platform === 'android') {
    try { await deps.prepare(); }
    catch (error) {
      if ((error as { code?: string })?.code === 'ATTESTATION_UNSUPPORTED') throw error;
    }
  }
  if (platform === 'ios') {
    try {
      const registered = await deps.registerIos(studentId!);
      if (!registered.ok) return registered;
    } catch (error) {
      const code = (error as { code?: string })?.code;
      if (code === 'ATTESTATION_UNSUPPORTED') throw error;
      if (code !== 'ATTESTATION_UNAVAILABLE' && code !== 'TIMEOUT') return rejected();
    }
    if (await deps.getStudentId() !== studentId) return rejected();
  }
  const photo = await deps.capture(supported ? { studentId: studentId!, reservationId } : undefined);
  if (!photo) return { ok: false, statusCode: 'SSU0000', message: '인증샷 촬영이 취소되었습니다' };

  try {
    if (supported && !photo.captureId) return rejected();
    const form = new FormData();
    form.append('idx', String(reservationId));
    form.append('file', photoBlob(photo), photo.name);
    form.append('turnstileToken', await deps.turnstile());

    if (supported) {
      if (await deps.getStudentId() !== studentId) return rejected();
      const issuedAt = deps.now();
      const response = await deps.challenge(reservationId);
      if (!response.ok) return response;
      const challenge = response.data;
      if (
        !challenge || challenge.studentId !== studentId || challenge.reservationId !== reservationId ||
        challenge.purpose !== 'VERIFY_PHOTO_UPLOAD' || !/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(challenge.challenge) ||
        !Number.isFinite(challenge.expiresInSeconds) || challenge.expiresInSeconds <= 0 || challenge.expiresInSeconds > 60
      ) return rejected();
      const deadline = issuedAt + challenge.expiresInSeconds * 1000;
      if (deps.now() >= deadline) return rejected();
      form.append('platform', platform!);
      form.append('challenge', challenge.challenge);
      try {
        const proof = await deps.attest({ captureId: photo.captureId!, studentId: studentId!, reservationId, challenge: challenge.challenge });
        if (
          !proof || proof.platform !== platform || typeof proof.attestation !== 'string' ||
          !proof.attestation || proof.attestation.length > 32 * 1024 || /\s/.test(proof.attestation)
        ) return rejected();
        if (platform === 'ios') {
          if (typeof proof.keyId !== 'string' || !/^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(proof.keyId)) return rejected();
          if (!/^[A-Za-z0-9+/]+={0,2}$/.test(proof.attestation)) return rejected();
          form.append('keyId', proof.keyId);
        } else if (proof.keyId !== undefined) return rejected();
        form.append('attestation', proof.attestation);
      } catch (error) {
        const code = (error as { code?: string })?.code;
        if (code === 'ATTESTATION_UNSUPPORTED') throw error;
        if (code !== 'ATTESTATION_UNAVAILABLE' && code !== 'TIMEOUT') return rejected();
        // 부분 입력으로 서버에 전달한다. 관찰 모드의 허용/강제 모드의 거부는 서버가 결정한다.
      }
      if (deps.now() >= deadline || await deps.getStudentId() !== studentId) return rejected();
    }
    return await deps.upload(form);
  } finally {
    if (photo.captureId) await deps.release(photo.captureId).catch(() => {});
  }
}
