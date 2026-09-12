# 인증샷 App Attestation 계약 v1

## 구현 범위

현재 구현은 설정·키 배포, 인증된 사용자의 `POST /attest/challenge`, challenge 저장·소모, 공통 요청 바이트 생성, Android Play Integrity 서버 검증과 인증샷 업로드 연결까지다. `ATTESTATION_ENFORCE=false`에서는 증명 누락·실패를 기록하고 업로드를 허용하며, `true`에서는 `SSU4206`으로 거부한다.

Android 네이티브 증명 생성·프론트엔드 연결, iOS 검증기·키 등록·네이티브 연결은 후속 작업이다. 현재 앱은 아직 증명을 보내지 않으므로 기본 관찰 모드를 유지한다. iOS도 아직 검증할 수 없기 때문에 양 플랫폼 연동과 실기기 검증을 완료하기 전에는 전역 강제 모드를 켜지 않는다. 운영 배포와 내부 테스트 트랙의 실제 앱 토큰 검증은 아직 수행하지 않았다.

## 계정과 배포 설정

| 항목 | 값 |
| --- | --- |
| GCP 프로젝트 | `ssutoday-d1873` |
| Cloud 프로젝트 번호 | `997341830534` |
| 서비스 계정 | `play-integrity-verifier@ssutoday-d1873.iam.gserviceaccount.com` |
| Android package / iOS Bundle ID | `com.ssutoday` |
| Apple Team ID / App ID Prefix | `F7TW6722Y9` |
| iOS App ID | `F7TW6722Y9.com.ssutoday` |
| GitHub Actions Secret | `PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON` (JSON 원문) |
| 운영 서버 파일 | `ssutoday-v3/.secrets/attestation/play-integrity.json` |
| API 컨테이너 파일 | `/run/secrets/attestation/play-integrity.json` |

앱 서명 인증서의 SHA-256 지문은 `.env.example`의 `PLAY_APP_SIGNING_CERTIFICATE_SHA256`에 있다. 콜론으로 구분한 16진수 지문이며 여러 인증서는 쉼표로 구분한다. Android 검증기는 Google 응답의 Base64url 인증서 해시를 디코딩해 같은 32바이트인지 비교한다. 응답의 모든 서명 지문이 허용 목록에 있어야 한다. 서명 키 교체 기간에는 기존 키와 새 키 지문을 함께 설정한다. 잘못된 지문이나 package 설정은 기동 시 거부한다.

`common.yml`에는 위 공개 식별자의 기본값이 있다. 운영 `.env` 원문을 수정하지 않아도 식별자가 적용된다. 환경별 변경은 `.env.example`의 환경변수로 덮어쓴다. Docker의 API 서비스는 `PLAY_INTEGRITY_CREDENTIALS`를 컨테이너 경로로 설정한다. 로컬 JVM 실행은 이 환경변수에 저장소 밖 JSON 파일의 경로를 지정한다.

배포 workflow는 Secret을 검사하고 Base64로 전송한 뒤 서버에서 권한 600의 파일로 원자적으로 교체한다. 원문 JSON을 원격 셸 코드에 삽입하지 않는다. Secret이 비어 있으면 기존 서버 키를 보존한다. `.secrets/`는 Git 및 Docker 빌드에서 제외하고 API 컨테이너에만 읽기 전용으로 마운트한다. 키가 없는 개발 환경에서는 빈 디렉터리를 마운트할 수 있다.

`ATTESTATION_ENFORCE=false`가 기본값이다. 검증기와 업로드 연결, 실기기 검증, 관찰 운영을 마친 뒤에만 강제 적용한다. `APP_ATTEST_PRODUCTION=true`는 TestFlight/App Store 환경의 검증 기준이며 개발용 키와 운영용 키를 혼용하지 않는다.

## challenge 발급 (구현됨)

`POST /attest/challenge`는 기존 인증 쿠키와 `@LoginStudent`를 사용한다. 학생 ID를 요청 본문에서 받지 않는다.

사진 업로드:

```json
{"purpose":"VERIFY_PHOTO_UPLOAD","reservationId":42}
```

iOS 키 등록:

```json
{"purpose":"APP_ATTEST_REGISTER"}
```

업로드 용도는 양수 `reservationId`가 필수이며 기존 `getForPhotoUpload` 정책으로 소유자, 예약 상태와 업로드 가능 시간을 확인한다. 등록 용도에는 `reservationId`를 보내지 않는다. 잘못된 조합은 `SSU4000`, 예약 정책 실패는 기존 `SSU4200`~`SSU4204`다.

응답 예시:

```json
{
  "statusCode":"SSU2000",
  "data":{
    "challenge":"AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
    "expiresInSeconds":60,
    "studentId":20260000,
    "purpose":"VERIFY_PHOTO_UPLOAD",
    "reservationId":42
  },
  "message":"성공"
}
```

challenge는 `SecureRandom` 32바이트를 padding 없는 Base64url로 표현한 43자 문자열이다. TTL은 Redis 저장 시점부터 60초다. 서로 독립적인 값을 발급하므로 여러 기기나 요청이 서로의 challenge를 덮어쓰지 않는다.

Redis에는 `attestChallenge:v1:<challenge>` 문자열 키로 scope를 저장한다. `SET NX`와 TTL을 함께 적용하고, Lua에서 scope 일치 확인과 삭제를 원자적으로 실행한다. scope는 학생·용도·예약의 조합이다. 만료, 재사용, 불일치는 `SSU4206`이며 불일치 요청은 원본 키를 삭제하지 않는다.

단순 `@RedisHash` repository의 조회 후 삭제는 두 동시 요청이 모두 성공할 수 있어 사용하지 않는다. Redis 클라이언트는 adapter에 두고 domain service는 core의 `AttestationChallengeStorePort`에 의존한다.

challenge 소모는 플랫폼 증명 검증 성공 후 DB 상태 변경 직전에 domain service를 통해 호출한다. 별도 소모 HTTP API는 만들지 않는다. Redis 소모는 JPA 롤백으로 복구되지 않는다. 소모 후 업로드나 저장이 실패하면 새 challenge와 증명을 발급받아 재시도한다. 관찰 모드에서도 미사용·유효 증명만 성공으로 기록한다.

## 요청 바이트와 해시 (구현됨)

`AttestationClientData`가 기준 구현이다. 아래 필드를 순서대로 UTF-8로 인코딩한다. 줄바꿈은 LF 하나이고 마지막 줄 뒤에도 LF가 있다. 공백, BOM, CRLF는 넣지 않는다. 숫자는 선행 0 없는 양의 십진수다.

사진 업로드:

```text
ssutoday.attestation.v1
purpose=VERIFY_PHOTO_UPLOAD
studentId=20260000
reservationId=42
challenge=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8
photoSha256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
```

`photoSha256`은 업로드할 최종 JPEG 바이트의 SHA-256을 소문자 16진수 64자로 표현한다. data URI 문자열이나 Base64 텍스트를 해싱하지 않는다. 서버는 기존 `UploadPhotoCommand.input`으로 받은 바이트에서 계산하고, 같은 바이트를 저장소 업로드에 사용한다. multipart 프레임과 다른 파트는 사진 바이트에 포함하지 않는다.

iOS 키 등록:

```text
ssutoday.attestation.v1
purpose=APP_ATTEST_REGISTER
studentId=20260000
challenge=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8
keyId=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
```

`keyId`는 Apple이 생성한 32바이트 키 식별자의 표준 Base64(padding 포함)다. 위 예시는 인코딩 검사용 데이터로 실제 App Attest 키가 아니다.

플랫폼별 사용:

- Android: `requestHash = Base64urlNoPadding(SHA256(clientDataBytes))`.
- iOS: `clientDataHash = SHA256(clientDataBytes)`의 32바이트를 `attestKey` / `generateAssertion`에 전달한다.
- 서버: 인증 사용자와 요청, 실제 사진 바이트로 `clientDataBytes`를 재구성한다. 클라이언트가 보낸 사진 해시나 직렬화 결과를 신뢰하지 않는다.

위 예시의 SHA-256 Base64url 결과는 업로드 `bF7nJGb7o2lT2ogovdDp0VPtyO_xoHxNgqb3OaAcDaU`, 등록 `pA9dtkbbjzQnPIvkgb0Le6Bjf5ThvCbeXzTpQNLbImY`다. 공통 테스트의 고정 벡터이며 네이티브에서도 동일하게 검증해야 한다.

## Android 서버 검증 (구현됨)

`PlayIntegrityVerificationPort`의 adapter가 서비스 계정으로 `playintegrity` OAuth scope의 토큰을 얻고 Google의 `POST https://playintegrity.googleapis.com/v1/com.ssutoday:decodeIntegrityToken`을 호출한다. 클라이언트가 제공한 JSON 판정이나 요청 해시를 직접 신뢰하는 경로는 없다. 키는 최초 사용 시 읽고 OAuth 토큰은 만료 전까지 캐시를 재사용한다. 키를 교체하면 API 프로세스를 재시작한다. 키가 없으면 `NOT_CONFIGURED`, OAuth/통신 장애는 `PROVIDER_UNAVAILABLE`, decode HTTP 401/403은 `PROVIDER_AUTHORIZATION_FAILED`로 기록한다.

Google decode 연결 제한은 3초, 응답 제한은 5초다. 같은 증명의 decode를 명시적으로 재시도하거나 검증 결과를 캐시하지 않는다. Google의 재사용 방지와 별도로 Redis challenge의 원자적 소모가 성공해야 최종 `VERIFIED`다. 외부 오류의 응답 본문이나 예외 메시지는 토큰을 포함할 수 있으므로 로그에 전달하지 않는다.

| 검증 항목 | 조건 |
| --- | --- |
| `requestDetails.requestPackageName` | 설정의 package와 일치 |
| `requestDetails.requestHash` | 인증 학생·예약·challenge·실제 사진 바이트로 재계산한 값과 일치 |
| `requestDetails.timestampMillis` | Google int64 문자열 형식, 현재 기준 과거 120초부터 미래 30초 이내 |
| `appIntegrity.appRecognitionVerdict` | `PLAY_RECOGNIZED` |
| `appIntegrity.packageName` | 설정의 package와 일치 |
| `appIntegrity.certificateSha256Digest` | 비어 있지 않으며 모든 지문이 허용 목록에 포함 |
| `deviceIntegrity.deviceRecognitionVerdict` | 배열에 정확히 `MEETS_DEVICE_INTEGRITY` 포함 |
| challenge | 발급 후 60초 이내이며 같은 학생·업로드 용도·예약에 귀속, 미사용 |

`MEETS_BASIC_INTEGRITY`만 있거나 `UNEVALUATED`인 판정은 실패다. `accountDetails.appLicensingVerdict`는 현재 필수 조건에 포함하지 않는다. `PLAY_RECOGNIZED`만으로 모든 사이드로드를 구분할 수 있다고 가정하지 않으며, Google Play에서 알려진 앱/서명과 기기 무결성을 확인하는 정책이다.

## 업로드 API와 후속 네이티브 계약

### iOS 키 등록 (아직 미구현)

`POST /attest/register`는 인증된 사용자에게서 `keyId`, `challenge`, `attestation`을 받는다. `attestation`은 `attestationObject` 바이트의 표준 Base64다. 등록용 client data로 Apple 체인·nonce·공개키와 keyId 일치·App ID·counter·환경을 검증하고 등록용 challenge를 소모한다. 검증한 공개키는 인증 사용자에게 귀속한다. 다른 학생의 등록 키를 재등록으로 빼앗을 수 없도록 keyId 유일성과 소유권을 강제한다.

최초 등록과 재설치 후 재등록은 업로드용 challenge 발급 전에 완료한다. 네이티브 키 생성만 성공한 상태를 서버 등록 완료로 취급하지 않는다. 서버 응답을 잃은 재시도 정책과 학생 계정 전환 시 키 소유권 처리는 iOS 구현 시 테스트에 포함한다.

### 인증샷 업로드 (서버 구현됨)

기존 multipart `POST /reserve/verifyPhoto/upload`에 다음 필드를 nullable/default null로 추가했다. 기존 `idx`, `file`, `turnstileToken`은 유지한다.

| 필드 | 내용 |
| --- | --- |
| `platform` | `android` 또는 `ios` |
| `challenge` | `VERIFY_PHOTO_UPLOAD` 용도로 발급한 값 |
| `attestation` | Android는 Google 토큰 원문, iOS는 assertion 바이트의 표준 Base64 |
| `keyId` | iOS에서 서버에 등록한 키 식별자. Android는 생략 |

별도 `clientData`나 `photoSha256` 필드는 사용하지 않는다. 인증 학생 ID는 `@LoginStudent`에서 가져온다. Turnstile과 기존 예약 소유권·상태·업로드 시간 정책을 먼저 확인한 뒤 사진을 한 번 읽고, 같은 바이트를 검증과 스토리지 업로드에 사용한다. 스토리지 길이도 실제 바이트 수를 사용한다. 신규 필드는 아래 기준으로 판정하며 관찰 모드에서 모두 업로드를 허용하고 강제 모드에서 `VERIFIED`만 허용한다.

| 요청/검증 상태 | 판정 |
| --- | --- |
| 신규 필드 모두 생략 | `MISSING` |
| 부분 입력, 빈 증명, 잘못된 challenge, Android의 keyId 포함 | `INVALID_INPUT` |
| 공백이 포함되거나 32KiB를 초과하는 Android 토큰 | `INVALID_INPUT` |
| iOS 또는 알 수 없는 플랫폼 | `UNSUPPORTED_PLATFORM` (iOS 구현 전까지) |
| Google 판정 거부 | `REQUEST_HASH_MISMATCH`, `APP_UNRECOGNIZED`, `DEVICE_UNTRUSTED` 등 |
| challenge 만료·재사용·학생/예약/용도 불일치 | `CHALLENGE_REJECTED` |
| Redis 소모 실패 | `CHALLENGE_STORE_UNAVAILABLE` |
| Google 판정과 challenge 소모 모두 성공 | `VERIFIED` |

로그에는 학생·예약 ID, 정규화한 플랫폼, 판정, enforce 값만 남긴다. 업로드가 커밋되면 기존 Discord 촬영 알림에 판정과 관찰/강제 모드를 함께 표시한다. 토큰·challenge·키·사진 원문은 이 기록에 포함하지 않는다. 관찰 모드에서도 유효한 challenge를 소모하며 재사용을 성공으로 기록하지 않는다.

iOS 구현 시 keyId의 소유자를 검사하고 counter를 DB에서 원자적으로 증가시켜야 한다.

### 브리지와 촬영 흐름 (아직 미구현)

네이티브는 카메라 촬영·압축 결과를 임시 `captureId`에 연결해 보관한다. `security.attest`는 웹이 보낸 임의 사진 해시나 바이트를 서명하는 기능으로 제공하지 않는다. `captureId`로 찾은 실제 촬영 바이트에서 해시를 계산하고 응답의 학생·예약·challenge에 바인딩한다. 캡처 핸들의 만료, 계정 전환 시 초기화, 신뢰하는 WebView origin의 호출만 허용하는 검사도 네이티브 구현 범위다.

등록 준비 → 촬영·압축 → Turnstile 완료 → 업로드용 challenge 발급 → 증명 생성 → 같은 사진 바이트 업로드 순서로 처리한다. 촬영 중이나 최초 키 등록 중에 업로드용 60초 TTL이 소진되지 않도록 한다.

## 검증

- 전체: `./gradlew build` (Windows: `.\gradlew.bat build`, JDK 21).
- Redis adapter 테스트는 Testcontainers의 독립 Redis 7 컨테이너로 TTL, 만료, scope 불일치, 중복 저장, 16개 동시 소모를 검사한다. Docker가 없으면 해당 통합 테스트는 skip되므로 Docker가 있는 환경에서 실행 결과를 확인한다.
- Android 검증 테스트는 Google 형식의 합성 응답으로 필드 변조, 누락, 시간 경계, 인증서 교체, 오류/빈 응답을 검사한다. 테스트용 RSA 키와 로컬 OAuth 서버로 서명·scope·토큰 캐시를 확인하며 실제 서비스 계정 키를 사용하지 않는다.
- 업로드 테스트는 사진/학생/예약/challenge 변경, 동일 challenge 재사용, 관찰/강제 모드, 동일 바이트 저장, 커밋 후 알림, 구버전 multipart와 인증 학생 주입을 검사한다.
- 서비스 계정 OAuth 발급과 잘못된 테스트 토큰의 `400 INVALID_ARGUMENT` 응답을 로컬에서 확인했다. 이는 계정 인증·기본 연결 확인이며 실제 앱 토큰의 검증이나 운영 서버 연결 확인을 대체하지 않는다.

## 근거 문서

- [Google Play Integrity 요청 바인딩 및 서버 검증](https://developer.android.com/google/play/integrity/standard)
- [Google Play Integrity 판정 필드와 라이선스 구분](https://developer.android.com/google/play/integrity/verdicts)
- [Apple App Attest 서버 검증과 App ID](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server)
- [Apple App Attest 환경](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.devicecheck.appattest-environment)
- [Spring Data Redis 원자적 스크립트 실행](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
