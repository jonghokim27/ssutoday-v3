# SSUTODAY

SSUTODAY는 숭실대학교 학생을 위한 공지 확인, 스터디룸 예약, 예약 알림, 모바일 WebView 앱을 하나의 저장소에서 관리하는 프로젝트다. 서버는 Kotlin/Spring Boot 멀티 모듈로 구성되어 있고, 웹 프론트엔드는 React/Vite, 모바일 앱은 Expo/React Native WebView 래퍼로 구성되어 있다.

## 구성 요약

| 영역 | 경로 | 역할 |
| --- | --- | --- |
| 서버 API | `ssutoday-api` | HTTP API, 인증 필터, 공통 응답, 컨트롤러 |
| 서버 application | `ssutoday-application` | use case, 트랜잭션 경계, 외부 이벤트 발행 |
| 서버 domain | `ssutoday-domain` | 엔티티, repository, domain service, 정책 객체 |
| 서버 core | `ssutoday-common/ssutoday-core` | 공통 예외, 상태 코드, 메시지, port 인터페이스 |
| 서버 adapter | `ssutoday-common/ssutoday-adapter` | Kafka, Firebase, JWT, Cloudflare R2, uSaint, Turnstile, Discord 구현체 |
| 서버 batch | `ssutoday-batch` | 공지 크롤링, 예약 인증샷/알림 스케줄 작업 |
| 서버 consumer | `ssutoday-consumer` | Kafka 예약 요청/푸시 메시지 소비 |
| 웹 프론트엔드 | `frontend` | React 기반 사용자 웹앱 |
| 모바일 앱 | `mobile` | Expo Router 기반 RN WebView 래퍼 앱 |
| 인프라 | `infra` | Redis/Kafka/API/Batch/Consumer Docker Compose |
| 스크립트 | `scripts` | 프론트엔드-모바일 브리지 프로토콜 동기화 검증 |

## 전체 흐름

사용자는 웹 또는 모바일 앱에서 SSUTODAY 웹앱에 접속한다. 모바일 앱은 실제 기능 화면을 WebView로 띄우고, 카메라, 푸시 토큰, 생체 인증, Turnstile, 외부 브라우저 같은 네이티브 기능은 브리지 프로토콜로 웹앱과 통신한다.

서버는 API 모듈이 HTTP 요청을 받고 application service가 use case를 실행한다. domain service는 JPA/Redis repository를 통해 도메인 상태를 조회하거나 변경한다. 외부 시스템 연동은 core port를 통해 application/domain에서 추상화하고, 실제 구현은 adapter 모듈이 제공한다.

예약 생성은 즉시 예약을 확정하지 않고 Kafka 예약 요청 토픽으로 넘길 수 있다. consumer가 예약 요청을 처리하고, 처리 결과와 알림은 push/Discord 흐름으로 이어진다. batch는 공지 크롤링, 인증샷 미촬영 예약 취소, 예약 시작/종료/인증샷 촬영 알림을 주기적으로 수행한다.

## 요구 환경

- JDK 21
- Gradle Wrapper
- Node.js 22 권장
- npm
- MySQL 8 호환 DB
- Redis
- Kafka
- Firebase Admin credential JSON
- Cloudflare R2 또는 S3 호환 스토리지
- Cloudflare Turnstile secret/site key
- Discord webhook URL은 선택 사항
- Expo/EAS CLI는 모바일 빌드 시 필요

## 서버

### 기술 스택

- Kotlin 2.2.21
- Spring Boot 4.0.6
- Spring Web, Security, Validation, Data JPA, Data Redis, Kafka, Actuator
- MySQL Connector/J
- Firebase Admin SDK
- JSoup
- JJWT
- Cloudflare R2는 S3 호환 API로 접근
- ktlint Gradle plugin

### 모듈 의존 방향

```text
ssutoday-api       \
ssutoday-batch      -> ssutoday-application -> ssutoday-domain -> ssutoday-common:ssutoday-core
ssutoday-consumer  /              \                                  ^
                                  \----------------------------------/

ssutoday-common:ssutoday-adapter -> ssutoday-common:ssutoday-core
ssutoday-common:ssutoday-adapter -> ssutoday-domain
```

실행 모듈(`api`, `batch`, `consumer`)은 adapter를 `runtimeOnly`로 포함한다. application/domain은 Kafka, Firebase, R2, Discord 같은 인프라 구현체를 직접 의존하지 않고 core port만 의존한다.

### 서버 모듈 상세

#### `ssutoday-api`

HTTP 표현 계층이다.

- `ApiApplication`: API 실행 진입점, 기본 타임존 설정
- `common`: `ApiResponse`, `ResponseStatus`, `GlobalControllerAdvice`, `ApiResponseWriter`
- `config`: Spring Security, CORS, cookie token writer, `@LoginStudent` argument resolver
- `article`: 공지 목록/상세/별표 API
- `reservation`: 예약 요청, 상태 조회, 목록, 취소, 인증샷 업로드, 관리자 액션, 이용 종료 API
- `room`: 스터디룸 목록/상세 API
- `student`: 로그인, 프로필, 로그아웃, 토큰 갱신 API
- `device`: 기기 등록, 해제, 버전 확인, 알림 옵션 API
- `sso`: 외부 SSO 토큰 생성/검증 API

#### `ssutoday-application`

use case 계층이다. 트랜잭션 경계를 갖고 domain service와 core port를 조합한다.

- `ArticleApplicationService`: 공지 목록/상세, 별표, 크롤링 upsert와 새 공지 push 발행
- `ReservationCommandApplicationService`: 예약 요청 생성, 예약 확정 처리, 취소, 이용 종료, 관리자 액션, 인증샷 누락 취소, 예약 알림 발송
- `ReservationQueryApplicationService`: 예약 내역, 룸별 예약 현황, 예약 요청 상태 조회
- `VerifyPhotoApplicationService`: 인증샷 업로드, Turnstile 검증, 스토리지 저장, Discord 알림
- `RoomApplicationService`: 룸 상세/목록 조회
- `StudentApplicationService`: uSaint 로그인, JWT/refresh token 발급과 검증, 로그아웃
- `DeviceApplicationService`: 기기 등록, 푸시 토큰, 알림 옵션, 강제 업데이트 여부
- `SsoApplicationService`: SSO client 인증, token 생성/검증
- `PushMessageApplicationService`: push sender port를 통한 직접 푸시 발송

#### `ssutoday-domain`

도메인 모델과 repository 계층이다.

- `article`: `Article`, `ArticleStar`, 공지 검색, 별표, upsert
- `reservation`: `Reservation`, `ReservationRequest`, `VerifyPhoto`, 예약 정책, 충돌 검사, 인증샷 상태, 관리자 token
- `room`: 접근 가능한 스터디룸 조회와 룸 view 변환
- `student`: `Student`, `Device`, `DeviceOption`, `Version`, `RefreshToken`
- `sso`: `SsoClient`, `SsoToken`
- `config`: 운영 설정 플래그
- 각 도메인의 `factory`: entity를 domain view로 변환

#### `ssutoday-common:ssutoday-core`

공통 계약과 메시지를 둔다.

- `StatusCode`: API 응답 상태 코드
- `messages.properties`: 상태 코드별 메시지
- `BusinessException`, `TokenExpiredException`
- `PushMessage`, `PushMessages`, `TokenPayload`, `ExternalStudentIdentity`
- port: `TokenPort`, `StudentAuthenticationPort`, `FileStoragePort`, `TurnstileVerificationPort`, `RecaptchaVerificationPort`, `ReservationRequestPublisher`, `PushMessagePublisher`, `PushMessageSender`, Discord 알림 port
- `afterCommit {}`: DB commit 이후 외부 이벤트를 실행하기 위한 트랜잭션 유틸

#### `ssutoday-common:ssutoday-adapter`

외부 연동 구현체다.

- `auth`: uSaint 인증 adapter
- `discord`: Discord webhook appender, 인증샷/예약 관리자 액션 알림
- `kafka`: 예약 요청과 push message publisher
- `push`: Firebase Admin 기반 push sender, mock sender
- `recaptcha`: Google reCAPTCHA 검증 adapter
- `turnstile`: Cloudflare Turnstile 검증 adapter
- `security`: JWT token adapter
- `storage`: Cloudflare R2 storage adapter

#### `ssutoday-batch`

스케줄 작업 실행 모듈이다.

- `ArticleCrawlJob`: `ssutoday.crawler.fixed-delay` 값 기준으로 공지 크롤링
- `CseCrawler`, `SsuCatchCrawler`, `StudentCouncilCrawler`: 공지 제공처별 크롤러
- `crawler/base`: JSoup/GNU board 기반 공통 크롤링과 날짜 파싱
- `ReservationPhotoCheckJob`: 매 분 인증샷 미촬영 예약 취소
- `ReservationNotificationJob`: 이용 시작/종료 5분 전 알림, 블록 시작 인증샷 촬영 알림

#### `ssutoday-consumer`

Kafka consumer 실행 모듈이다.

- `ReservationRequestConsumer`: 예약 요청 토픽을 소비해 예약 확정 흐름 실행
- `PushMessageConsumer`: push message 토픽을 소비해 Firebase 발송 실행

### API 엔드포인트 개요

모든 응답은 공통 `ApiResponse` 형식으로 래핑된다. 인증이 필요한 API는 cookie 기반 access/refresh token과 `@LoginStudent` 흐름을 사용한다.

#### 공지

- `POST /article/list`: 공지 목록 조회
- `POST /article/get`: 공지 상세 조회
- `POST /article/star`: 공지 별표 추가
- `POST /article/unstar`: 공지 별표 제거
- `POST /article/starred-count`: 사용자의 별표 공지 개수 조회

#### 예약

- `POST /reserve/request`: 예약 요청 생성
- `POST /reserve/status`: 예약 요청 처리 상태 조회
- `POST /reserve/list`: 예약 내역 조회
- `POST /reserve/cancel`: 예약 취소
- `POST /reserve/verifyPhoto/upload`: 인증샷 업로드
- `POST /reserve/adminTools`: 관리자 예약 액션 실행
- `POST /reserve/done`: 이용 종료 처리
- `GET /admin/action`: Discord 관리자 액션 확인 페이지
- `POST /admin/action`: Discord 관리자 액션 실행

#### 룸

- `POST /room/list`: 접근 가능한 스터디룸 목록 조회
- `POST /room/get`: 스터디룸 상세와 예약 현황 조회

#### 학생과 인증

- `POST /student/login`: uSaint 계정으로 로그인
- `POST /student/profile`: 로그인 사용자 프로필 조회
- `POST /student/logout`: 로그아웃
- `POST /student/updateXnApiToken`: 학생 API 토큰 갱신
- `POST /sso/generateToken`: SSO token 생성
- `POST /sso/validateToken`: SSO token 검증

#### 기기

- `POST /device/register`: 기기와 push token 등록
- `POST /device/unregister`: 기기 등록 해제
- `POST /device/checkVersion`: 강제 업데이트 필요 여부 확인
- `POST /device/getOption`: 알림 옵션 조회
- `POST /device/updateOption`: 알림 옵션 변경

### 서버 환경 변수

`.env.example`을 `.env`로 복사한 뒤 로컬 값으로 수정한다.

| 변수 | 설명 |
| --- | --- |
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD` | MySQL 연결 정보 |
| `REDIS_HOST`, `REDIS_PORT` | Redis 연결 정보 |
| `KAFKA_HOST` | Kafka bootstrap server |
| `JWT_SECRET_KEY` | JWT 서명 secret |
| `TURNSTILE_SECRET_KEY` | Cloudflare Turnstile server secret |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 호환 스토리지 접근 키 |
| `AWS_S3_ENDPOINT`, `AWS_S3_REGION` | Cloudflare R2 또는 S3 호환 endpoint/region |
| `S3_PUBLIC_BASE_URL` | 인증샷 공개 URL base |
| `VERIFY_PHOTO_BUCKET` | 인증샷 저장 bucket |
| `FIREBASE_CREDENTIALS` | Firebase Admin credential JSON 경로 |
| `DISCORD_ERROR_WEBHOOK_URL` | WARN 이상 로그 Discord 알림 webhook |
| `DISCORD_VERIFYPHOTO_WEBHOOK_URL` | 인증샷/예약 액션 Discord 알림 webhook |
| `ADMIN_BASE_URL` | Discord 관리자 액션 링크 base URL |

### 서버 실행

Windows PowerShell:

```powershell
Copy-Item .env.example .env
.\gradlew.bat build
.\gradlew.bat :ssutoday-api:bootRun
```

macOS/Linux:

```bash
cp .env.example .env
./gradlew build
./gradlew :ssutoday-api:bootRun
```

모듈별 실행:

```bash
./gradlew :ssutoday-api:bootRun
./gradlew :ssutoday-batch:bootRun
./gradlew :ssutoday-consumer:bootRun
```

기본 API 서버 포트는 `8090`이다. Docker Compose에서는 API 컨테이너를 `8080:80`으로 매핑하고 있으므로 compose 설정을 사용할 때는 포트 설정을 함께 확인해야 한다.

### Docker 실행

```bash
docker compose -f infra/docker-compose.yml up --build
```

Compose는 Redis, Kafka, API, batch, consumer를 올린다. `consumer`는 루트의 `firebase.json`을 `/run/secrets/firebase.json`으로 read-only mount한다.

루트 `Dockerfile`은 `MODULE` build arg로 실행 모듈을 선택한다.

```bash
docker build --build-arg MODULE=ssutoday-api -t ssutoday-api .
docker build --build-arg MODULE=ssutoday-batch -t ssutoday-batch .
docker build --build-arg MODULE=ssutoday-consumer -t ssutoday-consumer .
```

### 서버 테스트와 포맷

```bash
./gradlew test
./gradlew build
./gradlew ktlintCheck
./gradlew ktlintFormat
```

중요 테스트:

- `ReservationPolicyTest`, `ReservationRequestPolicyTest`, `ReservationCompletionPolicyTest`
- `ApiResponseContractTest`
- `LoginStudentArgumentResolverTest`
- `ArticleDateParserTest`, `ArticleCrawlJobTest`, `ArticleCrawlerIntegrationTest`
- `JwtTokenAdapterTest`, `TurnstileVerificationAdapterTest`, `RecaptchaVerificationAdapterTest`

## 프론트엔드

### 기술 스택

- React 19
- TypeScript 5.7
- Vite 6
- React Router 7
- CSS Modules

### 실행

```bash
cd frontend
npm install
npm run dev
npm run build
npm run preview
```

현재 API base URL과 Turnstile site key는 `frontend/src/shared/config/env.ts`에 정의되어 있다.

```ts
export const API_BASE_URL = 'https://api.ssu.today/';
export const TURNSTILE_SITE_KEY = '0x4AAAAAADr46yw6OJ_nBs_W';
```

로컬 API를 바라보게 하려면 해당 파일을 로컬 환경에 맞게 조정해야 한다.

### 프론트엔드 구조

```text
frontend/src
  app/                 앱 루트, router, route guard, provider, auth session
  pages/               라우트 단위 페이지
  features/
    auth/              로그인 랜딩, 약관, SSO callback, auth API
    my/                마이페이지, 기기/알림 옵션
    notice/            공지 목록/상세 링크/별표
    reservation/       예약 홈, 상세, 내역, 성공/실패, 예약 API
  shared/
    api/               apiClient와 공통 API 타입
    assets/            앱 아이콘 리소스
    config/            API URL, 앱 버전, Turnstile site key
    layout/            앱 레이아웃, 헤더, 하단 내비게이션
    native/            WebView 브리지 프로토콜과 transport
    recaptcha/         reCAPTCHA helper
    routing/           SafeAreaNavigate와 safe area parameter helper
    storage/           appStorage
    styles/            global.css, tokens.css
    turnstile/         Turnstile helper
    ui/                Button, Badge, Dialog, Toast, Loading 등 공통 UI
```

### 프론트엔드 라우트

- `/landing`: 로그인 랜딩
- `/terms`: 약관 동의
- `/sso_callback`: SSO callback
- `/reservations`: 예약 홈
- `/reservations/:roomId`: 예약 상세
- `/reservations/success`: 예약 성공
- `/reservations/failed`: 예약 실패
- `/reservations/history`: 예약 내역
- `/notices`: 공지사항
- `/my`: 마이페이지
- `/coming-soon`: 준비 중 화면

인증이 필요한 라우트는 `ProtectedRoute`가 감싸고, 로그인 사용자만 접근 가능하다. 공개 라우트는 `PublicOnlyRoute`로 관리한다.

### 주요 기능

- uSaint 기반 로그인과 SSO callback 처리
- 쿠키 기반 인증 세션 확인
- 공지 목록 조회, 링크 열기, 별표
- 스터디룸 목록과 날짜별 예약 현황 조회
- 예약 요청, 예약 상태 polling, 예약 성공/실패 처리
- 예약 내역, 이용 종료, 인증샷 촬영/업로드
- 마이페이지 알림 옵션 조회/수정
- 네이티브 앱 환경에서 WebView bridge로 카메라, 푸시, 생체 인증, 앱 설정, 외부 브라우저, Turnstile 호출
- SafeArea query/session 값을 이용한 모바일 WebView 여백 처리

### WebView 브리지

프론트엔드의 브리지 정의는 `frontend/src/shared/native/bridgeProtocol.ts`에 있다. 모바일 앱의 `mobile/src/bridge/protocol.ts`와 내용이 같아야 한다.

지원 method:

- `device.getInfo`
- `push.requestPermission`
- `push.getToken`
- `push.subscribeTopic`
- `push.unsubscribeTopic`
- `browser.openExternalUrl`
- `system.openAppSettings`
- `camera.requestPermission`
- `camera.captureVerifyPhoto`
- `auth.signWithBiometrics`
- `network.checkConnectivity`
- `security.getTurnstileToken`

지원 event:

- `push.opened`
- `deeplink.navigate`
- `app.foregroundChanged`
- `network.changed`

브리지 요청 기본 timeout은 `10_000ms`다.

## 모바일 앱

### 기술 스택

- Expo SDK 54
- Expo Router 6
- React 19.1
- React Native 0.81.5
- React Native WebView
- React Native Firebase App/Messaging
- Expo Notifications
- Expo Image Picker
- Expo Local Authentication
- Expo NetInfo

### 실행

```bash
cd mobile
npm install
npm run start
npm run android
npm run ios
```

Expo/EAS 빌드:

```bash
cd mobile
npx eas build --profile development
npx eas build --profile preview
npx eas build --profile production
```

`mobile/eas.json`은 EAS CLI `>= 20.4.0`을 요구하고, production build에서 `autoIncrement`를 사용한다.

### 앱 설정

`mobile/app.config.js`가 Expo 설정의 기준이다.

- 앱 이름: `슈투데이`
- slug: `ssutoday`
- scheme: `ssutoday`
- version: `3.0.0`
- iOS bundle identifier: `com.ssutoday`
- Android package: `com.ssutoday`
- orientation: portrait
- new architecture enabled
- splash image: `./assets/splash-icon.png`, width `150`
- iOS camera/FaceID permission message 정의
- Android camera/notification policy permission 정의
- Firebase config file은 환경 변수로 조건부 주입
  - `GOOGLE_SERVICE_INFO_PLIST`
  - `GOOGLE_SERVICES_JSON`
- iOS Podfile에는 config plugin으로 `GoogleUtilities`에만 `:modular_headers => true`를 삽입한다.

### 모바일 구조

```text
mobile
  app/
    _layout.tsx       Expo Router root layout
    index.tsx         메인 WebView 화면 진입
    browser.tsx       인앱 브라우저 화면 진입
  src/
    bridge/
      protocol.ts     프론트엔드와 공유해야 하는 브리지 타입
      registry.ts     네이티브 bridge handler 등록
    screens/
      WebViewScreen.tsx       웹앱을 표시하는 메인 WebView
      InAppBrowserScreen.tsx  공지/인증샷 등 내부 브라우저
      OfflineScreen.tsx       오프라인 화면
      TurnstileModal.tsx      RN 전용 Turnstile WebView modal
    utils/
      deepLink.ts     deep link 처리
```

### 모바일 브리지 역할

모바일 앱은 웹앱 화면을 WebView로 보여주되, 웹에서 직접 처리하기 어려운 기능을 네이티브로 대신 실행한다.

- `device.getInfo`: 플랫폼, 앱 버전, 기기 정보 제공
- `push.requestPermission`: 알림 권한 요청
- `push.getToken`: Firebase FCM token 획득
- `browser.openExternalUrl`: 외부 URL 또는 인앱 브라우저 열기
- `camera.captureVerifyPhoto`: 인증샷 촬영 후 data URI 전달
- `auth.signWithBiometrics`: 생체 인증
- `network.checkConnectivity`: 네트워크 상태 확인
- `security.getTurnstileToken`: RN WebView modal로 Turnstile token 발급

푸시 알림은 Firebase Messaging token을 서버에 등록하고, 포그라운드 메시지는 `expo-notifications` 로컬 알림으로 표시한다.

### WebView 대상

메인 WebView는 배포된 프론트엔드 URL을 로드한다. SmartID/uSaint, Turnstile, 공지 링크 등 외부 도메인 허용 정책은 `WebViewScreen`과 브리지 처리 흐름을 함께 확인해야 한다.

## 브리지 프로토콜 동기화

프론트엔드와 모바일은 같은 bridge protocol 타입을 각각 보유한다.

- `frontend/src/shared/native/bridgeProtocol.ts`
- `mobile/src/bridge/protocol.ts`

두 파일은 주석을 제외한 코드가 같아야 한다. 검증 스크립트:

```bash
node scripts/check-bridge-protocol-sync.js
```

GitHub Actions의 `bridge-protocol-sync.yml`은 main push와 PR에서 두 파일 변경 시 동기화를 검증한다.

## 배포

### 프론트엔드 R2 배포

`.github/workflows/deploy-r2.yml`은 프론트엔드 빌드 산출물을 Cloudflare R2에 배포한다. 워크플로는 `frontend` 디렉터리에서 npm 의존성을 설치하고 Vite build를 수행한다.

### 서버 배포

서버는 루트 `Dockerfile`의 `MODULE` build arg로 실행 모듈별 jar를 빌드한다.

```bash
docker build --build-arg MODULE=ssutoday-api -t ssutoday-api .
docker build --build-arg MODULE=ssutoday-batch -t ssutoday-batch .
docker build --build-arg MODULE=ssutoday-consumer -t ssutoday-consumer .
```

운영 환경에서는 MySQL, Redis, Kafka, Firebase credential, R2 credential, Turnstile secret, Discord webhook을 별도로 주입해야 한다.

### 모바일 배포

모바일 앱은 EAS Build를 사용한다. Firebase config 파일은 저장소에 커밋하지 않고 EAS secret 또는 환경 변수로 주입한다.

```bash
cd mobile
npx eas build --profile production
```

## 개발 규칙

- 서버 작업 전에는 루트 `AGENTS.md`의 계층 규칙을 따른다.
- API 계층은 HTTP 표현과 인증 사용자 주입만 담당한다.
- application 계층은 use case와 트랜잭션 경계를 담당한다.
- domain 계층은 도메인 상태와 정책을 담당한다.
- 외부 연동은 core port와 adapter 구현으로 분리한다.
- 새 서버 상태 코드는 `StatusCode`, `messages.properties`, API 응답 계약 테스트를 함께 확인한다.
- 프론트엔드와 모바일 브리지 protocol을 변경하면 양쪽 파일을 동시에 갱신하고 동기화 스크립트를 실행한다.
- `.env`, `firebase.json`, Google service config, `.kotlin/`, build output, IDE 파일은 커밋하지 않는다.

## 자주 쓰는 명령

루트:

```bash
./gradlew build
./gradlew test
./gradlew ktlintCheck
docker compose -f infra/docker-compose.yml up --build
node scripts/check-bridge-protocol-sync.js
```

프론트엔드:

```bash
cd frontend
npm install
npm run dev
npm run build
```

모바일:

```bash
cd mobile
npm install
npm run start
npm run android
npm run ios
npx eas build --profile production
```
