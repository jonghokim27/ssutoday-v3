# 예약·인증샷 App Attestation 계약 v1

## 구현 범위

일반 예약 요청과 인증샷 업로드에 Android Play Integrity / iOS App Attest 검증을 적용한다. `ATTESTATION_ENFORCE=false`가 기본값이며, 구버전 payload와 증명 누락·실패를 허용하고 판정을 Discord에 기록한다. `true`에서는 `VERIFIED`만 허용하고 나머지는 `SSU4206`으로 저장 전에 거부한다. 기존 로그인·Turnstile·예약 정책은 두 모드 모두 유지한다. iOS 키 등록은 관찰 모드에서도 검증을 통과해야 저장한다.

웹·서버는 아직 운영 배포하지 않았으며 내부 테스트 트랙과 TestFlight의 실제 앱 증명 검증은 배포 후 확인한다. Android 3.0.2(17)와 iOS 3.0.2(45) EAS 배포 빌드가 완료됐으며 iOS의 TestFlight 제출도 성공했다. IPA의 서명된 실행 파일에서 production App Attest 권한과 앱 ID를 확인했다. 양 플랫폼 실기기 검증을 완료하기 전에는 전역 강제 모드를 켜지 않는다.

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

예약 요청은 `{"purpose":"RESERVATION_CREATE"}`로 발급한다. 아직 예약 ID가 없으므로 `reservationId`는 생략하고, 응답의 `reservationId`는 null이다. scope는 로그인 학생과 용도에 귀속되고, 방·날짜·시간은 아래 서명 바이트에 묶인다. 기존 관리자 전용 예약 생성 API는 이 일반 예약 요청 범위에 포함하지 않는다.

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

예약 요청:

```text
ssutoday.attestation.v1
purpose=RESERVATION_CREATE
studentId=20260000
roomNoUtf8Hex=31
date=2026-09-14
startBlock=20
endBlock=23
challenge=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8
```

방 번호 문자열 `1`의 UTF-8 바이트를 소문자 hex로 직렬화한 값이 `31`이다. 구분자나 줄바꿈이 포함돼도 필드 경계가 바뀌지 않는다. 마지막 LF를 포함한 예시의 SHA-256 Base64url은 `Id4rRexJVUuXGMctfmccH9sWWo0t_d6yNwA5-N5_yrc`이며 Kotlin·Android·Swift에서 같은 고정 벡터를 확인한다. 서버는 로그인 학생과 실제 예약 요청 필드로 해시를 재계산한다.

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

## 업로드 API와 네이티브 계약

### 예약 요청

`POST /reserve/request`의 기존 JSON 필드(`turnstileToken`, `roomNo`, `date`, `startBlock`, `endBlock`)를 유지하고 `platform`, `challenge`, `attestation`, `keyId`를 선택 항목으로 추가한다. 새 필드가 없는 구버전 JSON도 그대로 역직렬화되고, 관찰 모드에서는 Google·Apple·키 저장소 호출 없이 기존 접수 경로로 진행한다.

신버전 앱은 provider/키 준비 → 예약용 challenge → `security.attestReservation` → 예약 요청 순서다. 네이티브는 학생·방·날짜·시작/종료 블록·challenge로 직접 해시를 만든다. 웹이 임의 해시를 전달하는 서명 API는 제공하지 않는다. iOS 예약과 사진은 같은 등록 키의 counter를 공유한다. 클라이언트에서도 계정 변경과 challenge 만료를 확인한다.

관찰 모드 예약 판정은 DB 커밋 후 기존 `DISCORD_VERIFY_PHOTO_WEBHOOK_URL`로 학생 ID·요청 ID·판정·모드만 비동기 전송한다. 토큰·assertion·challenge·개인키를 전송하지 않는다. 사진 판정은 기존 인증샷 Discord 알림에 포함한다. Discord 설정 누락이나 전송 실패가 이미 접수한 예약을 거부하지 않는다. 강제 모드의 거부는 서버 로그에 판정을 남기며 접수 완료 알림을 보내지 않는다.

### 지원하지 않는 기기

App Attest 미지원 또는 Android의 Play Integrity API/Play 서비스 미지원은 `ATTESTATION_UNSUPPORTED`로 구분한다. 예약과 사진에서 기존 앱 전용 모달 스타일로 `해당 기기에서<br>지원하지 않는 기능이에요`를 표시하며 버튼은 `닫기` 하나다. 설치 유도 버튼은 없다. 일시적인 통신 장애와 미지원은 구분한다. 이 모달은 새 capability를 제공하는 앱에 적용되며, 구버전 앱의 기존 요청 흐름에는 추가하지 않는다.

### iOS 키 등록 (구현됨)

`POST /attest/register`는 인증된 사용자에게서 `keyId`, `challenge`, `attestation`을 받는다. `attestation`은 `attestationObject` 바이트의 표준 Base64다. 등록용 client data로 Apple 체인·nonce·공개키와 keyId 일치·App ID·counter·환경을 검증하고 등록용 challenge를 소모한다. 검증한 공개키는 인증 사용자에게 귀속한다. 다른 학생의 등록 키를 재등록으로 빼앗을 수 없도록 keyId 유일성과 소유권을 강제한다.

응답은 `{"statusCode":"SSU2000","data":{"keyId":"<등록한 키>"},"message":"성공"}`이다. 입력 형식 오류는 `SSU4000`, 증명·challenge·소유권·환경 검증 실패는 `SSU4206`이다.

서버는 번들에 고정한 Apple App Attestation Root CA로 인증서 체인과 유효기간을 확인한다. leaf nonce 확장, App ID 해시, 등록 counter 0, 환경별 AAGUID, credential ID, P-256 공개키의 해시와 COSE 좌표를 검증한다. CBOR 크기·중첩·중복 키·잔여 바이트를 제한한다. 새 Apple validation category/bundle version 확장이 있으면 형식과 배포 환경도 검사하며, 확장이 없는 기존 형식은 허용한다.

`device_attestation`에는 학생, keyId, 공개키, 운영/개발 환경, 등록 요청 fingerprint, counter를 저장한다. keyId는 대소문자를 구분하는 ASCII 44자 유일 키다. 한 학생의 여러 설치·기기를 허용한다. fingerprint는 `SHA256(clientDataHash || decodedAttestationObject)`이며 증명 원문은 저장하지 않는다. 동일 학생·환경·fingerprint의 재등록 요청은 기존 키를 반환하고 counter를 초기화하지 않는다. 서버 성공 응답을 잃어 challenge가 이미 소모됐더라도 정확히 같은 등록 요청으로 복구할 수 있다.

최초 등록과 재설치 후 재등록은 촬영과 업로드용 challenge 발급 전에 완료한다. 네이티브 키 생성만 성공한 상태를 서버 등록 완료로 취급하지 않는다. 앱은 학생·환경별로 keyId, 서버 등록 여부와 미승인 등록 요청을 설치 영역에 저장하고 백업에서 제외한다. 개인키는 DeviceCheck가 관리한다. 서버 성공 응답의 keyId를 확인한 뒤에만 네이티브 등록 상태를 승인한다. 미승인 요청이 남으면 새 challenge나 Apple 등록 호출 없이 그대로 서버에 재전송한다. 서버가 등록을 명확히 거부하면 해당 학생의 키만 폐기하고 새 키로 한 번 재시도한다. 네트워크 오류와 Apple `serverUnavailable`은 기존 키/요청을 보존하고, `invalidKey`는 해당 계정 키를 다시 준비한다.

DB는 `ddl-auto: validate`이므로 배포 전에 [실제 DDL](../infra/sql/20260913-device-attestation.sql)을 대상 MySQL에 적용해야 한다. 이 변경에서 운영 DB에 SQL을 실행하지 않았다. 초기 `plan.md`의 예시 DDL 대신 이 파일을 사용한다.

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
| 알 수 없는 플랫폼 | `UNSUPPORTED_PLATFORM` |
| Google 판정 거부 | `REQUEST_HASH_MISMATCH`, `APP_UNRECOGNIZED`, `DEVICE_UNTRUSTED` 등 |
| iOS 미등록 키·다른 학생 키·다른 환경 | `KEY_NOT_REGISTERED`, `KEY_OWNER_MISMATCH`, `ENVIRONMENT_MISMATCH` |
| iOS 서명·App ID·counter 실패 | `SIGNATURE_INVALID`, `APP_ID_MISMATCH`, `COUNTER_REJECTED` 등 |
| iOS 키 조회·counter 저장 실패 | `KEY_STORE_UNAVAILABLE` |
| challenge 만료·재사용·학생/예약/용도 불일치 | `CHALLENGE_REJECTED` |
| Redis 소모 실패 | `CHALLENGE_STORE_UNAVAILABLE` |
| 플랫폼 검증·challenge 소모·iOS counter 갱신 성공 | `VERIFIED` |

로그에는 학생·예약 ID, 정규화한 플랫폼, 판정, enforce 값만 남긴다. 업로드가 커밋되면 기존 Discord 촬영 알림에 판정과 관찰/강제 모드를 함께 표시한다. 토큰·challenge·키·사진 원문은 이 기록에 포함하지 않는다. 관찰 모드에서도 유효한 challenge를 소모하며 재사용을 성공으로 기록하지 않는다.

iOS는 등록된 키의 학생·환경을 확인한 뒤 `SHA256withECDSA`로 `authenticatorData || clientDataHash`의 서명을 검증한다. 이미 해싱한 nonce를 이 알고리즘에 넣어 이중 해싱하지 않는다. App ID와 uint32 counter를 확인하고, challenge 소모 후 학생·환경·`counter < 새 counter` 조건을 포함한 DB UPDATE가 정확히 한 행을 변경해야 성공한다. 동시 요청은 같은 counter로 두 번 성공할 수 없다. counter 갱신은 업로드 DB 트랜잭션과 함께 롤백되지만 Redis challenge는 복원되지 않는다.

### 양 플랫폼 브리지와 촬영 흐름 (구현됨)

Android와 iOS는 앱 내부 `expo-camera`의 `CameraView`에서 촬영한다. 촬영 결과를 기존 규격(JPEG, 너비 1280, 품질 0.8)으로 압축하고, 로컬 Expo 모듈 `mobile/modules/ssutoday-attestation`이 앱 cache의 최종 파일을 읽는다. 같은 바이트의 Base64 data URI와 SHA-256을 만들고 해시·학생·예약을 임시 UUID `captureId`에 연결한다. 웹이 받은 사진을 교체하면 서버가 재계산한 해시가 증명에 바인딩된 해시와 달라진다.

`storeCapture(uri, studentId, reservationId)`는 RN 내부에서만 사용한다. 웹에 노출되는 `security.attest`는 `captureId`, `studentId`, `reservationId`, `challenge`만 받으며, 사진 URI·사진 해시·임의 client data를 받아 서명하지 않는다. 네이티브가 보관한 촬영 해시로 계약 바이트를 재구성한다. 핸들은 monotonic clock 기준 120초 동안 유효하며 증명 시도 시 원자적으로 한 번만 소모한다. 새 촬영, 로그아웃·계정 변경, 페이지 이동·리로드·화면 이탈 시 폐기하고, 이미 진행 중인 SDK 응답도 폐기된 핸들에는 반환하지 않는다. 취소·실패·업로드 완료 후 프론트엔드도 핸들을 해제한다.

메인 WebView의 준비된 `https://v3.ssu.today` 문서에서만 브리지 요청을 처리한다. 양 플랫폼에서 네이티브가 만든 난수를 메인 프레임 JS 클로저에 주입하고 요청마다 붙인다. 하위 프레임에도 브리지가 노출되는 구형 Android WebView에서 외부 iframe이 메인 페이지 URL로 메시지를 보내는 경우에도 난수가 없으면 거부한다. 문서가 교체되면 이전 비동기 응답을 전달하지 않는다. 프론트엔드는 handshake를 최대 10초 기다린 후 capability를 확인하고 요청을 전송하며, 외부 프레임의 handshake/응답도 무시한다. 앱이 백그라운드로 들어가면 촬영 핸들을 폐기한다.

Google Standard Integrity provider 준비 작업은 공유·캐시하고, provider 무효 오류에만 한 번 재준비한다. 증명 생성은 총 25초 제한이며 브리지 제한은 30초다. 촬영 전에 provider 준비 결과를 기다려 미지원 기기를 안내한다. SDK 오류 원문·토큰은 브리지 오류나 로그에 넣지 않는다.

iOS 등록 브리지는 `security.prepareAppAttest` → `security.attestRegister` → 서버 `/attest/register` → `security.confirmAppAttest` 순서다. 폐기는 `security.resetAppAttest`로 요청하며 학생·keyId가 일치할 때만 적용한다. 학생별 actor 작업 잠금으로 동시 키 생성을 막는다. DeviceCheck 키 생성/assertion은 25초, 최초 attestation은 45초로 제한한다. 지연 콜백은 continuation을 다시 완료할 수 없다. iOS Expo 모듈은 CocoaPods로 자동 연결되며 Swift 핵심 로직은 같은 소스를 Swift Package에서도 테스트한다.

등록 준비 → 촬영·압축 → Turnstile 완료 → 업로드용 challenge 발급 → 증명 생성 → 같은 사진 바이트 업로드 순서로 처리한다. 촬영 중이나 최초 키 등록 중에 업로드용 60초 TTL이 소진되지 않도록 한다.

프론트엔드는 challenge의 학생·예약·용도·TTL·인코딩을 확인하고, challenge API 요청 시작부터 경과 시간을 계산한다. 증명 생성 전후 만료와 계정 변경을 검사한다. SDK 통신 장애/시간 초과만 `platform`·`challenge`를 포함하고 증명은 생략해 서버의 관찰/강제 정책에 맡긴다. 핸들 거부·잘못된 요청 등은 업로드 전에 재촬영 오류로 종료한다. 증명 capability가 없는 구버전 앱은 기존 multipart 흐름을 유지한다.

앱 버전은 `3.0.2`이다. 새 바이너리 빌드가 필요하며, `runtimeVersion.policy=appVersion`으로 구버전 바이너리에 이 JS 번들이 OTA 적용되지 않도록 한다. iOS 빌드 변수 `APP_ATTEST_ENVIRONMENT`는 기본 `production`이며 entitlement와 설치 상태 저장 폴더에 함께 적용된다. 개발 환경은 `development`와 서버 `APP_ATTEST_PRODUCTION=false`를 함께 사용한다. TestFlight/App Store는 `production`과 서버 `true`를 사용한다.

EAS Build는 App Attest entitlement의 capability 동기화를 지원하지만, 이번 비대화형 빌드에서는 기존 프로파일을 재사용해 권한 누락으로 실패했다. Apple API도 `APP_ATTEST` 추가 요청을 지원하지 않아 개발자 콘솔에서 직접 활성화한 뒤, 기존 배포 인증서로 새 프로파일을 발급해 EAS에 적용했다. 새 프로파일의 `F7TW6722Y9.com.ssutoday`, App Attest 허용 환경과 기존 production 푸시 권한을 확인했다. 추후에도 capability 변경 후 실제 프로파일을 확인한다. [Expo iOS capabilities](https://docs.expo.dev/build-reference/ios-capabilities/)

## 실기기 검증에서 고친 것

합성 fixture로는 통과하던 iOS assertion 검증이 실기기(iPadOS 26.0.1, iPad)에서 전부 거부됐다. 원인은 두 가지이며 모두 서버 검증기 문제였다. 앱과 프로토콜은 바꾸지 않았다.

`authenticatorData`의 flags를 고정값으로 강제하고 있었다. 기존 검사는 assertion에서 AT 비트가 꺼져 있고 bit 1~5가 모두 0이어야 통과시켰는데, 실기기는 assertion에도 AT 비트를 세팅한다(`size=37 flags=0x40`). Apple은 App Attest의 flags를 문서로 보장하지 않는다. AT 비트는 attested credential data를 파싱하는 등록에서만 의미가 있으므로 등록에서만 강제한다. assertion 뒤에 붙는 바이트는 기존 크기 기준 검사가 그대로 처리한다.

assertion 서명의 해시 단계가 하나 부족했다. Apple은 `nonce = SHA256(authenticatorData || clientDataHash)`를 만든 뒤 **그 nonce를 메시지로** ECDSA-SHA256 서명하므로 최종 서명 대상은 `SHA256(nonce)`다. 기존 구현은 `authenticatorData`와 `clientDataHash`를 그대로 `SHA256withECDSA`에 넣어 nonce 자체를 다이제스트로 삼았다. 실기기 assertion을 오프라인에서 대조해 여섯 가지 조합 중 `ECDSA(SHA256(SHA256(authData || clientDataHash)))`만 유효함을 확인했다.

테스트가 이를 잡지 못한 이유는 테스트 헬퍼가 구현과 같은 방식으로 서명했기 때문이다. 양쪽이 같은 오해를 공유하면 통과한다. 헬퍼를 실기기와 같은 방식으로 바꾸고, 기존 방식이 거부되는지를 검사하는 항목을 남겼다. 실기기가 보내는 flags 조합도 테스트에 포함했다.

관찰 모드가 두 건을 모두 잡아냈다. `ATTESTATION_ENFORCE=true`였다면 iOS 사용자의 예약과 인증샷이 전부 차단됐을 것이다. 합성 입력으로 하는 검증은 실기기 확인을 대체하지 못한다.

## 검증

- 전체: `./gradlew build` (Windows: `.\gradlew.bat build`, JDK 21).
- Redis adapter 테스트는 Testcontainers의 독립 Redis 7 컨테이너로 TTL, 만료, scope 불일치, 중복 저장, 16개 동시 소모를 검사한다. Docker가 없으면 해당 통합 테스트는 skip되므로 Docker가 있는 환경에서 실행 결과를 확인한다.
- Android 검증 테스트는 Google 형식의 합성 응답으로 필드 변조, 누락, 시간 경계, 인증서 교체, 오류/빈 응답을 검사한다. 테스트용 RSA 키와 로컬 OAuth 서버로 서명·scope·토큰 캐시를 확인하며 실제 서비스 계정 키를 사용하지 않는다.
- 업로드 테스트는 사진/학생/예약/challenge 변경, 동일 challenge 재사용, 관찰/강제 모드, 동일 바이트 저장, 커밋 후 알림, 구버전 multipart와 인증 학생 주입을 검사한다.
- iOS 서버 검증은 합성 P-256 인증서로 정상 등록과 필드 변조를 검사한다. Apple 공개 예제의 실제 인증서 체인도 검사하지만, 예제의 nonce 입력이 문서 계약과 달라 `NONCE_MISMATCH`를 기대한다. 예제에 맞춰 검증을 완화하지 않았다. 자세한 차이는 [fixture 설명](../ssutoday-common/ssutoday-adapter/src/test/resources/attestation/README.md)에 기록했다. 실제 TestFlight 증명의 정상 통과 확인은 아직 필요하다.
- MySQL 8.4 Testcontainers 테스트는 실제 DDL과 JPA 매핑, keyId 대소문자 구분·유일성, 소유권·환경 조건, 8개 동시 counter 갱신 중 한 건만 성공함을 확인한다. Docker가 없는 환경에서는 skip되므로 실행 여부를 확인한다.
- JS 흐름·브리지 테스트: Node 24, `frontend`에서 `npm ci` 후 루트에서 `node --test scripts/attestation-flow.test.mjs scripts/reservation-attestation.test.mjs scripts/ios-registration.test.mjs scripts/bridge-security.test.mjs scripts/bridge-transport.test.mjs`. 촬영 바이트 전달, 계정 변경, 등록 응답 유실·재시도, 만료, 장애 처리, 외부 프레임 거부, handshake 대기를 검사한다.
- 프로토콜 동기화: `node scripts/check-bridge-protocol-sync.js`. 프론트엔드 빌드: `frontend`에서 `npm run build`. 모바일 타입 검사: `mobile`에서 `npx tsc --noEmit`.
- Android 네이티브 테스트: `mobile`에서 `npm ci`, 실제 Firebase 앱 설정 파일 경로를 `GOOGLE_SERVICES_JSON`에 지정하고 `npx expo prebuild --platform android --no-install` 후 `mobile/android`에서 `.\gradlew.bat :ssutoday-attestation:testDebugUnitTest` (JDK 21, Android SDK). 서버 고정 해시 벡터, 계정·예약 바인딩, TTL, 폐기, 8개 동시 소모를 검사한다.
- 모바일 JS 번들: `mobile`에서 `npx expo export --platform android --platform ios --output-dir ../build/attestation-mobile-bundle`. 이는 iOS 네이티브 컴파일이나 실기기 검증을 대체하지 않는다.
- Swift 핵심 테스트: `mobile/modules/ssutoday-attestation`에서 `swift test`. Linux는 Swift Crypto를 사용하고 Apple 플랫폼은 CryptoKit을 사용한다. 고정 바이트 벡터, 촬영 TTL·한 번 소모·폐기, 등록 승인, 학생별 키와 응답 유실 복구를 확인한다. Windows에서는 공식 `swift:6.1` 컨테이너로 실행할 수 있다.
- 로컬 서버 전체 빌드(106개 테스트, skip 없음), Swift 핵심 테스트 10개, JS 흐름·브리지 테스트 91개, Android 모듈 컴파일·테스트 6개, 모바일 타입 검사와 프론트엔드 빌드가 통과했다. 예약 HTTP 구버전 계약, 서명 필드 변조, 사진/등록/예약 용도 교차 사용, iOS 예약·사진 counter 공유, 양 모드와 커밋 후 알림을 포함한다. EAS Android 3.0.2(17)는 실제 빌드 설정으로 완료됐고, AAB에서 생성한 APK의 버전과 기존 서명 지문 일치를 확인했다. 합성 설정으로 하는 로컬 검사는 실제 Firebase·스토어 앱 동작 확인을 대체하지 않는다.
- 서비스 계정 OAuth 발급과 잘못된 테스트 토큰의 `400 INVALID_ARGUMENT` 응답을 로컬에서 확인했다. 이는 계정 인증·기본 연결 확인이며 실제 앱 토큰의 검증이나 운영 서버 연결 확인을 대체하지 않는다.

## 다음 검증과 배포 순서

배포 아티팩트: [Android 3.0.2(17)](https://expo.dev/accounts/joey0307/projects/ssutoday/builds/ec26f306-cd4d-4eaf-b126-490eb57ce68d), [iOS 3.0.2(45)](https://expo.dev/accounts/joey0307/projects/ssutoday/builds/f2a398aa-2d35-4a3b-bd1a-cf9e96c3f313), [TestFlight 제출 성공](https://expo.dev/accounts/joey0307/projects/ssutoday/submissions/d42fc17d-8daa-4b53-a99f-8ec00f845c68). Android AAB에서 생성한 universal APK는 기존 서명 인증서로 검증했다. APK SHA-256: `2ff2aeed26e00b927af7ab65c76e810c6440e9902464f6dbf8d36c9690719787`.

1. 대상 MySQL에 실제 DDL을 먼저 적용한 뒤 서버와 프론트엔드를 `ATTESTATION_ENFORCE=false` 상태로 반영한다. 운영 API에서 서비스 계정 파일과 Google 연결을 확인한다.
2. EAS 또는 Mac에서 iOS 전체 앱 빌드를 확인한다. App ID의 App Attest capability를 포함한 배포 프로비저닝으로 실제 Firebase 앱 설정을 사용하는 `3.0.2` 바이너리를 빌드하고 TestFlight로 설치한다. 최초 등록과 서버 `VERIFIED` 판정을 확인한다. 재실행·재설치·계정 전환과 등록 서버 응답 유실 후 재시도를 확인한다.
3. 실제 Firebase 앱 설정을 사용하는 Android `3.0.2` AAB를 빌드한다. Play Console 내부 테스트 트랙에 올리고 테스트 계정으로 Play에서 설치한다. 로컬 debug APK의 서명·배포 판정으로 운영 성공을 판단하지 않는다.
4. 양 플랫폼에서 예약 접수·정상 촬영 업로드, 카메라 취소·권한 거부, 재촬영, 로그아웃·계정 변경, 화면 이동·백그라운드 진입, 네트워크 끊김 후 재시도와 미지원 기기의 닫기 전용 모달을 확인한다. 구버전 앱의 예약과 업로드가 `false`에서 정상 처리되는지도 확인한다.
5. 통제된 테스트 환경에서 파일 교체, 다른 예약, 증명/challenge 재사용, 만료와 iOS counter 재사용을 검사한다. 관찰 모드는 실패를 기록해도 업로드를 허용하므로 `VERIFIED` 여부와 강제 모드의 `SSU4206`을 구분한다.
6. 양 플랫폼 실기기 검증, 구버전 앱 전환과 관찰 결과를 확인한 뒤 전역 강제 모드를 적용한다.

## 근거 문서

- [Google Play Integrity 요청 바인딩 및 서버 검증](https://developer.android.com/google/play/integrity/standard)
- [Google Play Integrity 판정 필드와 라이선스 구분](https://developer.android.com/google/play/integrity/verdicts)
- [Apple App Attest 서버 검증과 App ID](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server)
- [Apple App Attest 키 수명과 재시도](https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity)
- [Apple App Attest 환경](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.devicecheck.appattest-environment)
- [Spring Data Redis 원자적 스크립트 실행](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
