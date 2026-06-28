// 이 파일은 frontend/src/shared/native/bridgeProtocol.ts와 내용이 동일해야 합니다.
// Metro가 mobile/ 프로젝트 루트 밖의 파일을 번들링하지 못해 상대경로 import 대신 수동으로 복제했습니다.
// 동기화 여부는 scripts/check-bridge-protocol-sync.js (CI)에서 검증합니다.
export type NativePlatform = 'ios' | 'android';

export const BRIDGE_PROTOCOL_VERSION = 1 as const;

export const BRIDGE_REQUEST_TIMEOUT_MS = 10_000;

export type BridgeErrorCode = 'UNSUPPORTED_METHOD' | 'PERMISSION_DENIED' | 'INVALID_PARAMS' | 'TIMEOUT' | 'NATIVE_ERROR';

export type BridgeMethod =
  | 'device.getInfo'
  | 'push.requestPermission'
  | 'push.getToken'
  | 'push.subscribeTopic'
  | 'push.unsubscribeTopic'
  | 'browser.openExternalUrl'
  | 'system.openAppSettings'
  | 'camera.requestPermission'
  | 'camera.captureVerifyPhoto'
  | 'auth.signWithBiometrics'
  | 'network.checkConnectivity'
  | 'security.getTurnstileToken';

export type BridgeEvent = 'push.opened' | 'deeplink.navigate' | 'app.foregroundChanged' | 'network.changed';

export type BridgeEventPayloadMap = {
  'push.opened': { data: Record<string, string> };
  'deeplink.navigate': { url: string };
  'app.foregroundChanged': { state: 'foreground' | 'background' };
  'network.changed': { online: boolean };
};

export type BridgeHandshakeEnvelope = {
  v: 1;
  kind: 'handshake';
  id: string;
  capabilities: BridgeMethod[];
  platform: NativePlatform;
  appVersion: string;
  protocolVersion: 1;
};

export type BridgeRequestEnvelope = {
  v: 1;
  kind: 'request';
  id: string;
  method: BridgeMethod;
  params?: unknown;
};

export type BridgeResponseEnvelope =
  | { v: 1; kind: 'response'; id: string; ok: true; result: unknown }
  | { v: 1; kind: 'response'; id: string; ok: false; error: { code: BridgeErrorCode; message: string } };

export type BridgeEventEnvelope<E extends BridgeEvent = BridgeEvent> = {
  v: 1;
  kind: 'event';
  event: E;
  payload?: BridgeEventPayloadMap[E];
};

export type BridgeEnvelope = BridgeHandshakeEnvelope | BridgeRequestEnvelope | BridgeResponseEnvelope | BridgeEventEnvelope;

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function isBridgeEnvelope(value: unknown): value is BridgeEnvelope {
  if (!isPlainObject(value)) {
    return false;
  }

  if (value.v !== 1 || typeof value.kind !== 'string') {
    return false;
  }

  switch (value.kind) {
    case 'handshake':
      return typeof value.id === 'string' && Array.isArray(value.capabilities) && typeof value.platform === 'string' && typeof value.appVersion === 'string';
    case 'request':
      return typeof value.id === 'string' && typeof value.method === 'string';
    case 'response':
      return typeof value.id === 'string' && typeof value.ok === 'boolean';
    case 'event':
      return typeof value.event === 'string';
    default:
      return false;
  }
}

export function parseBridgeEnvelope(raw: string): BridgeEnvelope | null {
  try {
    const parsed = JSON.parse(raw);
    return isBridgeEnvelope(parsed) ? parsed : null;
  } catch {
    return null;
  }
}
