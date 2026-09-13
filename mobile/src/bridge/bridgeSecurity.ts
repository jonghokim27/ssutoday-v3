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
export function secureBridgeScript(token: string | null): string {
  if (!token) return '';
  return `
    (function() {
      if (window.top !== window || location.origin !== ${JSON.stringify(TRUSTED_ORIGIN)}) return;
      var bridge = window.ReactNativeWebView;
      if (!bridge || bridge.__ssutodaySecured) return;
      var original = bridge.postMessage.bind(bridge);
      var token = ${JSON.stringify(token)};
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
      window.ReactNativeWebView = secured;
    })();
  `;
}

export type CaptureScope = { studentId: number; reservationId: number };
export type AttestParams = CaptureScope & { captureId: string; challenge: string };

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
