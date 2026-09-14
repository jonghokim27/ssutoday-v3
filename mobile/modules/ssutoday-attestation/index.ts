import { requireOptionalNativeModule } from 'expo-modules-core';

export type AppAttestKeyState = {
  keyId: string;
  registered: boolean;
  pending?: { challenge: string; attestation: string };
};

type AttestationModule = {
  createBridgeToken(): string;
  clearCaptures(): void;
  releaseCapture(captureId: string): void;
  prepare(): Promise<void>;
  attestReservation(studentId: number, roomNo: string, date: string, startBlock: number, endBlock: number, challenge: string): Promise<{ platform: 'android' | 'ios'; attestation: string; keyId?: string }>;
  storeCapture(uri: string, studentId: number, reservationId: number): Promise<{ captureId: string; photoSha256: string; uri: string }>;
  attest(captureId: string, studentId: number, reservationId: number, challenge: string): Promise<{ platform: 'android' | 'ios'; attestation: string; keyId?: string }>;
  prepareAppAttest(studentId: number): Promise<AppAttestKeyState>;
  attestRegister(studentId: number, keyId: string, challenge: string): Promise<{ keyId: string; challenge: string; attestation: string }>;
  confirmAppAttest(studentId: number, keyId: string): Promise<void>;
  resetAppAttest(studentId: number, keyId: string): Promise<void>;
};

// 기존 바이너리는 모듈이 없으며 증명 capability를 광고하지 않는다.
export default requireOptionalNativeModule<AttestationModule>('SsutodayAttestation');
