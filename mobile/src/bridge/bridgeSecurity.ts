const TRUSTED_ORIGIN = 'https://v3.ssu.today';

export function isTrustedBridgeUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.origin === TRUSTED_ORIGIN && !url.username && !url.password;
  } catch {
    return false;
  }
}

export function canDispatchBridge(sourceUrl: string, pageUrl: string, ready: boolean, token: unknown, expectedToken: string | null): boolean {
  return ready && isTrustedBridgeUrl(sourceUrl) && isTrustedBridgeUrl(pageUrl) &&
    (expectedToken === null || token === expectedToken);
}

// 구형 Android WebView의 addJavascriptInterface는 하위 프레임에도 노출된다.
// 네이티브에서 만든 토큰을 메인 프레임의 클로저에만 두고 모든 요청에 붙인다.
// Android의 injectedJavaScriptBeforeContentLoaded는 문서 시작 시점 실행이 보장되지 않는다.
// 설치 여부만 보고 건너뛰면 한 번 실패한 문서를 영영 복구하지 못하므로, 주입될 때마다
// 원본 bridge에서 다시 설치한다. 원본은 토큰을 아는 호출자에게만 돌려준다.
export function secureBridgeScript(token: string | null): string {
  if (!token) return '';
  return `
    (function() {
      if (window.top !== window || location.origin !== ${JSON.stringify(TRUSTED_ORIGIN)}) return;
      var token = ${JSON.stringify(token)};
      var current = window.ReactNativeWebView;
      var bridge = current && current.__ssutodaySecured
        ? (typeof current.__ssutodayRaw === 'function' ? current.__ssutodayRaw(token) : null)
        : current;
      if (!bridge) return;
      var original = bridge.postMessage.bind(bridge);
      // Java에서 노출한 host object는 메서드를 재정의할 수 없을 수 있으므로 JS 객체로 감싼다.
      var secured = { postMessage: function(raw) {
        try {
          var message = JSON.parse(raw);
          if (!message || typeof message !== 'object' || Array.isArray(message)) return;
          message.bridgeToken = token;
          original(JSON.stringify(message));
        } catch (_) {}
      }};
      if (typeof bridge.injectedObjectJson === 'function') secured.injectedObjectJson = bridge.injectedObjectJson.bind(bridge);
      Object.defineProperty(secured, '__ssutodaySecured', { value: true });
      Object.defineProperty(secured, '__ssutodayRaw', { value: function (t) { return t === token ? bridge : null; } });
      window.ReactNativeWebView = secured;
    })();
  `;
}

export type CaptureScope = { studentId: number; reservationId: number };
export type AttestParams = CaptureScope & { captureId: string; challenge: string };
export type ReservationAttestParams = { studentId: number; roomNo: string; date: string; startBlock: number; endBlock: number; challenge: string };

export function isReservationAttestParams(value: unknown): value is ReservationAttestParams {
  if (!isAppAttestStudent(value)) return false;
  const input = value as Record<string, unknown>;
  return typeof input.roomNo === 'string' && input.roomNo.trim().length > 0 && input.roomNo.length <= 100 &&
    typeof input.date === 'string' && /^202[3-9]-[0-9]{2}-[0-9]{2}$/.test(input.date) &&
    Number.isInteger(input.startBlock) && Number(input.startBlock) >= 12 && Number(input.startBlock) <= 43 &&
    Number.isInteger(input.endBlock) && Number(input.endBlock) >= Number(input.startBlock) && Number(input.endBlock) <= 43 &&
    typeof input.challenge === 'string' && /^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(input.challenge);
}

export function isAppAttestStudent(value: unknown): value is { studentId: number } {
  if (!value || typeof value !== 'object') return false;
  const id = (value as { studentId?: unknown }).studentId;
  return Number.isSafeInteger(id) && Number(id) > 0 && Number(id) <= 2147483647;
}

export function isAppAttestKey(value: unknown): value is { studentId: number; keyId: string } {
  if (!isAppAttestStudent(value)) return false;
  const keyId = (value as { studentId: number; keyId?: unknown }).keyId;
  return typeof keyId === 'string' && /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(keyId);
}

export function isCaptureScope(value: unknown): value is CaptureScope {
  if (!value || typeof value !== 'object') return false;
  const input = value as Record<string, unknown>;
  return Number.isSafeInteger(input.studentId) && Number(input.studentId) > 0 && Number(input.studentId) <= 2147483647 &&
    Number.isSafeInteger(input.reservationId) && Number(input.reservationId) > 0;
}

export function isAttestParams(value: unknown): value is AttestParams {
  if (!isCaptureScope(value)) return false;
  const input = value as CaptureScope & Record<string, unknown>;
  return typeof input.captureId === 'string' && /^[0-9a-f-]{36}$/.test(input.captureId) &&
    typeof input.challenge === 'string' && /^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(input.challenge);
}
