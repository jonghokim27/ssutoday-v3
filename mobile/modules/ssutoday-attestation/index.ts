import { requireOptionalNativeModule } from 'expo-modules-core';

type AttestationModule = {
  createBridgeToken(): string;
  clearCaptures(): void;
  releaseCapture(captureId: string): void;
  prepare(): Promise<void>;
  storeCapture(uri: string, studentId: number, reservationId: number): Promise<{ captureId: string; photoSha256: string; uri: string }>;
  attest(captureId: string, studentId: number, reservationId: number, challenge: string): Promise<{ platform: 'android'; attestation: string }>;
};

// iOS와 기존 바이너리는 모듈이 없으며 Android 증명 capability를 광고하지 않는다.
export default requireOptionalNativeModule<AttestationModule>('SsutodayAttestation');
