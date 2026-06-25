# React Native(Expo) WebView 래퍼 앱 계획

## 0. 목표

- `frontend`(Vite/React 웹앱)를 변경 없이 그대로 웹 서비스로 유지한다.
- 동일한 웹앱을 Expo 기반 RN 앱이 WebView로 감싸서 배포하고, 앱에서만 제공하는 부가 기능(푸시, 카메라, 생체인증 등)을 추가로 노출한다.
- 웹 ↔ 앱 통신은 `react-native-webview`의 `postMessage`/`onMessage`를 사용하며, **새로운 부가 기능을 계속 추가해도 기존 코드를 갈아엎지 않도록** 메시지 구조를 표준화한다.
- iOS / Android 래퍼 앱 모두 바로 출시 가능한 수준까지 작성한다. (앱 아이콘, 권한 요청 메세지 등등 모두 작업)

### 현재 상태 확인 결과

- `frontend/src/shared/native/nativeBridge.ts`에 `NativeBridge` 인터페이스(11개 메서드)와 `MockNativeBridge`만 존재하고, 실제 네이티브 측 구현/메시지 송수신 코드는 이 저장소에 없다.
- `isNativeApp()`은 `navigator.userAgent.startsWith('SSUTODAY')`로만 판별하며, 네이티브일 때도 현재는 Mock이 그대로 호출된다(실제 앱 쪽에서 무엇을 어떻게 연동하고 있는지는 frontend 저장소만으로는 알 수 없음).
- 따라서 이 작업은 **기존 네이티브 연동을 리팩터링하는 게 아니라, 프로토콜과 앱 셸을 처음 설계/구축**하는 작업으로 간주한다. (만약 별도 운영 중인 네이티브 앱이 이미 있다면 마이그레이션 호환성 검토가 선행되어야 함 — §7 참고)

### WebView
- 앱은 WebView로 https://v3.ssu.today 를 열게 된다.
- 이 때, SafeArea inset 정보를 획득해서 URL을 로드하기 전 전달해야 한다.
- 쿼리 파라미터 종류와 이름은 frontend 프로젝트를 참고하면 된다.
- 인터넷 연결이 없을 때 다시 시도를 요청할 화면 1개는 React Native에서 직접 구현이 필요하다. 해당 화면의 디자인은 frontend 프로젝트와 통일한다.

---

## 1. 전체 아키텍처

```
┌─────────────────────────────┐
│   Expo App (mobile/)        │
│  ┌────────────────────────┐ │
│  │ WebViewScreen           │ │
│  │  - react-native-webview │ │
│  │  - bridge/transport.ts  │ │  <-- postMessage 송수신, ready handshake
│  │  - bridge/handlers/*    │ │  <-- capability별 네이티브 구현 (push/camera/...)
│  └────────────────────────┘ │
└─────────────┬────────────────┘
              │ postMessage (JSON envelope)
┌─────────────▼────────────────┐
│ Web App (frontend/, 그대로)   │
│  shared/native/               │
│   - nativeBridge.ts (호출 API)│
│   - bridgeTransport.ts (전송) │
│   - bridgeProtocol.ts (타입)  │
└────────────────────────────────┘
```

- 웹앱은 `frontend`에서 빌드된 정적 산출물(또는 운영 도메인 URL)을 그대로 WebView에 로드한다. 웹 코드 분기는 최소화하고, "네이티브 기능 호출"은 전부 `nativeBridge` 모듈 뒤로 숨긴다(현재 패턴 유지).
- 앱 전용 부가 기능은 **웹에서 시작하는 요청(request)**과 **앱에서 시작하는 이벤트(event)** 두 방향 모두 지원해야 한다(예: 푸시 수신, 딥링크, 앱 포그라운드 전환 등은 앱이 먼저 보낸다).

---

## 2. 메시지 프로토콜 설계

### 2.1 Envelope 형식

모든 메시지는 아래 4종 중 하나의 envelope로 직렬화된 JSON 문자열이다.

```ts
type BridgeEnvelope =
  | { v: 1; kind: 'handshake'; id: string; capabilities: string[]; platform: 'ios' | 'android'; appVersion: string; protocolVersion: 1 }
  | { v: 1; kind: 'request';  id: string; method: string; params?: unknown }
  | { v: 1; kind: 'response'; id: string; ok: true; result: unknown }
  | { v: 1; kind: 'response'; id: string; ok: false; error: { code: BridgeErrorCode; message: string } }
  | { v: 1; kind: 'event';    event: string; payload?: unknown };

type BridgeErrorCode =
  | 'UNSUPPORTED_METHOD'   // 해당 앱 버전에 capability 없음
  | 'PERMISSION_DENIED'
  | 'INVALID_PARAMS'
  | 'TIMEOUT'
  | 'NATIVE_ERROR';
```

- `v`: 프로토콜(envelope) 스키마 버전. breaking change 시에만 올린다.
- `method`/`event` 이름은 **네임스페이스.동작** 형태로 짓는다 (`device.getInfo`, `push.requestPermission`, `camera.capturePhoto`, `auth.signWithBiometrics`). 네임스페이스가 그대로 "부가 기능 카테고리" 단위가 되어, 신규 기능을 추가할 때 새 네임스페이스/메서드만 늘리면 된다.
- `request`/`response`는 `id`(uuid)로 1:1 매칭. `event`는 응답이 필요 없는 단방향 알림.
- `handshake`는 WebView 로드 직후 **앱이 먼저** 보낸다(웹이 앱의 존재를 몰라도 시작 가능). 웹은 핸드셰이크를 받기 전까지 네이티브 모드로 전환하지 않는다 → User-Agent 판별(`isNativeApp`)은 "웹뷰 안에서 동작 중"임을 빠르게 감지하는 1차 신호로만 쓰고, **실제 기능 가용 여부는 handshake의 `capabilities` 배열로 판단**한다.

### 2.2 통신 흐름

**웹 → 앱 (request/response)**
1. 웹: `id` 생성 → `{kind:'request', id, method, params}`를 `window.ReactNativeWebView.postMessage(JSON.stringify(...))`로 전송, `pendingMap[id]`에 resolve/reject/timeout 등록.
2. 앱: `onMessage`에서 파싱 → `method`를 capability 레지스트리에서 찾아 핸들러 실행 → 결과를 `{kind:'response', id, ok:true, result}` 또는 에러로 `webviewRef.current.postMessage(...)`를 통해 웹으로 전달.
3. 웹: `window`(iOS)와 `document`(Android, RN WebView 특성상 둘 다 리스닝 필요)의 `message` 이벤트에서 `id`로 매칭해 promise resolve/reject. 일정 시간(예: 10s) 내 응답 없으면 `TIMEOUT` 에러로 reject.

**앱 → 웹 (event)**
- 앱이 임의 시점에 `{kind:'event', event:'push.received', payload}`를 webview로 post. 웹은 `event` 이름 기준 구독자(리스너) 목록에 디스패치.
- 예: 푸시 알림 탭, 딥링크 진입, 앱 resume/foreground, 네트워크 상태 변경 등.

### 2.3 Capability negotiation (확장성의 핵심)

- 앱 빌드마다 지원하는 기능 집합이 다를 수 있다(웹은 즉시 배포되지만 앱은 스토어 심사 때문에 업데이트가 느림). 그래서 "네이티브냐 아니냐" 2분법이 아니라 **메서드 단위 가용성**을 둔다.
- 앱은 handshake에서 `capabilities: ['device.getInfo', 'push.requestPermission', ...]`처럼 자신이 실제로 구현한 메서드 목록을 보낸다.
- 웹의 `nativeBridge`는 `hasCapability(method)`를 제공하고, 미지원 시:
  - 구버전 앱에서 신기능을 호출하면 `UNSUPPORTED_METHOD`로 즉시 reject(네이티브 핸들러까지 안 가고 transport 레이어에서 차단) → "앱 업데이트가 필요해요" 안내로 분기 가능(현재 `requireNativeApp()`의 "앱에서만 가능해요" 모달과 구분).
  - 웹뷰가 아예 아닌 순수 브라우저는 handshake 자체가 없으므로 항상 Mock 경로(현재 동작 유지).

### 2.4 검증/보안

- 양쪽 모두 envelope을 받으면 **스키마 검증**(zod 등 경량 라이브러리, 또는 수동 타입가드) 후 처리. 알 수 없는 `kind`/형식 불일치는 무시 + 로그.
- WebView는 `onShouldStartLoadWithRequest`로 허용 도메인 외 네비게이션을 차단(메시지 채널이 신뢰 못 할 페이지로 빠지는 것 방지).
- 생체인증/결제처럼 민감한 동작은 네이티브가 웹의 요청 파라미터를 그대로 신뢰하지 않고, 네이티브 UI(시스템 프롬프트)로 사용자 확인을 받은 뒤 결과만 반환한다.
- `appVersion`/웹 빌드 버전을 handshake에서 교환해 로깅·원격 디버깅에 활용(불일치로 인한 문의 대응).

---

## 3. 코드 구조 변경안

### 3.1 `frontend` 쪽 (기존 파일 리팩터링)

```
frontend/src/shared/native/
  bridgeProtocol.ts    # envelope 타입, 에러 코드 (위 2.1)
  bridgeTransport.ts   # postMessage 전송, pending map, handshake 수신, capability 캐시, 이벤트 구독 API
  nativeBridge.ts       # 기존 NativeBridge 인터페이스 유지 + 각 메서드를 transport.request('device.getInfo', ...)로 구현
```

- 외부에 노출되는 `nativeBridge`, `isNativeApp`, `requireNativeApp` 시그니처는 최대한 유지해 호출부(`MyPageContent.tsx` 등) 변경을 최소화한다.
- `isNativeApp()`은 "웹뷰인지"의 신호로 남기고, 신규 기능 호출 가능 여부 체크는 `hasCapability('네임스페이스.메서드')`라는 새 헬퍼를 추가해 점진적으로 적용.
- 새 부가 기능 추가 시 웹 쪽 변경: `bridgeProtocol.ts`에 메서드/이벤트 이름 추가 → `nativeBridge.ts`에 타입 있는 래퍼 메서드 1개 추가. (transport 자체는 안 건드림)

### 3.2 `mobile/` (신규 Expo 프로젝트, 저장소 루트에 `frontend`와 형제 디렉터리로 생성 제안)

```
mobile/
  app/                      # expo-router 스크린 (단일 WebView 화면 + 필요 시 스플래시/권한 안내)
  src/bridge/
    protocol.ts              # frontend의 bridgeProtocol.ts와 동일 스펙(수동 동기화 또는 공유 패키지)
    registry.ts               # method/event 이름 -> 핸들러 매핑, capabilities 배열 생성
    handlers/
      device.ts                # device.getInfo
      push.ts                  # push.requestPermission/getToken/subscribeTopic/...
      camera.ts                 # camera.requestPermission/capturePhoto
      auth.ts                    # auth.signWithBiometrics
      browser.ts                  # browser.openExternalUrl, system.openAppSettings
      cookies.ts                   # webview.clearCookies/readCookie
      analytics.ts                  # analytics.logScreenView
    WebViewScreen.tsx          # postMessage 연결, onMessage 디스패치, handshake 발송
  app.json / eas.json
```

- 기존 `NativeBridge`의 11개 메서드를 §2의 네임스페이스 규칙으로 1:1 매핑해 `registry.ts`에 등록하는 것이 1차 작업. (예: `getDeviceInfo` → `device.getInfo`, `requestPushPermission` → `push.requestPermission`, `subscribePushTopic` → `push.subscribeTopic` ...)
- 신규 부가 기능 추가 시 앱 쪽 변경: `handlers/`에 핸들러 함수 추가 → `registry.ts`에 등록 한 줄 → capabilities 배열에 자동 포함. WebViewScreen이나 transport 로직은 안 건드림.

### 3.3 프로토콜 타입 공유 방법

`frontend`(Vite/web)와 `mobile`(Expo/RN)은 별도 패키지 매니저/번들러를 쓰므로 둘 중 하나를 선택해야 한다(§7 결정 필요 항목):
- (A) npm workspaces로 `packages/bridge-protocol`을 만들어 두 앱이 import.
- (B) 타입 파일을 손으로 동기화하고, CI에서 두 파일이 동일한지 diff 체크하는 스크립트로 drift 방지.

---

## 4. 기존 기능 마이그레이션 매핑

| 기존 NativeBridge 메서드 | 신규 method/event 이름 | 종류 |
|---|---|---|
| `getDeviceInfo` | `device.getInfo` | request |
| `requestPushPermission` | `push.requestPermission` | request |
| `getPushToken` | `push.getToken` | request |
| `subscribePushTopic` | `push.subscribeTopic` | request |
| `unsubscribePushTopic` | `push.unsubscribeTopic` | request |
| `openExternalUrl` | `browser.openExternalUrl` | request |
| `openAppSettings` | `system.openAppSettings` | request |
| `requestCameraPermission` | `camera.requestPermission` | request |
| `captureVerifyPhoto` | `camera.captureVerifyPhoto` | request |
| `signWithBiometrics` | `auth.signWithBiometrics` | request |
| `logScreenView` | `analytics.logScreenView` | request |
| (신규) 푸시 탭/딥링크 | `push.opened` / `deeplink.navigate` | event |

- openExternalUrl은 두가지 옵션을 제공해야 함. 새로운 WebView로 열기 (메인 WebView위에 Stack) / 외부 브라우저로 열기
- 이때 서브 WebView는 뒤로가기 버튼 + SafeArea 가리기 + 제목 표시 목적의 헤더가 있어야 한다. (기존 앱 프로젝트 NoticeWebViewScreen 참고)

---

## 5. 단계별 진행 계획

1. **프로토콜 확정** — 본 문서의 envelope/네이밍/에러코드를 최종 합의 (특히 §7의 공유 타입 방식 결정).
2. **frontend 리팩터링** — `bridgeProtocol.ts`/`bridgeTransport.ts` 추가, `nativeBridge.ts`를 실제 프로토콜 기반으로 교체. 웹뷰가 아닌 순수 브라우저에서는 여전히 Mock 그대로 동작하는지 확인(회귀 없음 보장).
3. **Expo 앱 셸 구축** — `mobile/` 생성, WebView 1개 화면, handshake 송신, `registry.ts` 골격.
4. **기존 13개 기능 포팅** — §4 매핑대로 네이티브 핸들러 구현(푸시는 expo-notifications, 카메라는 expo-image-picker/camera, 생체인증은 expo-local-authentication 등 활용).
5. **통합 검증** — `frontend` dev 서버 또는 스테이징 URL을 Expo WebView로 로드해 마이페이지 알림 설정 플로우(현재 보고 있던 `MyPageContent.tsx`)로 회귀 테스트.
6. **신규 부가 기능 1개 시범 추가** — 확장성 검증용으로 기존에 없던 기능 하나를 끝까지 추가해보고 "핸들러 추가 → capability 자동 포함 → 웹에서 호출" 흐름이 매끄러운지 확인.
7. **빌드/배포 파이프라인** — EAS Build, 버전-capability 호환성 표 운영 문서화.

---

## 6. 테스트/검증 전략

- 프로토콜 단위 테스트: envelope 직렬화/역직렬화, pending map의 timeout/매칭 로직.
- 앱 쪽 capability 레지스트리: 등록된 메서드 이름 중복/네임스페이스 규칙 검사(lint 스크립트).
- 수동 E2E: Android/iOS 실기기에서 WebView 로드 → handshake 수신 → 알림 토글 → 백그라운드 전환 후 이벤트 수신까지 확인.

---

## 7. 결정이 필요한 사항 (Open Questions)

1. `mobile/` 디렉터리를 저장소 루트로 작업
2. 프로토콜 타입 공유 방식: 어차피 모노레포이기 때문에 제일 깔끔한 방법으로 진행
3. 현재 운영 중인 실제 네이티브 앱은 (D:/projects/ssutoday-app)에 있지만 하위 호환은 전혀 고려하지 않아도 됨. 외부 서비스 설정 키 등 설정 파일 참고용으로만 사용.
4. 푸시 알림은 FCM 사용
