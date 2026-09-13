# 인증샷 무결성 강화 계획

> 구현 진행: 계정 설정과 기본 API 연결 확인, 설정·배포 연결, 요청 바이트 계약, challenge 발급·원자적 소모, Android/iOS 서버 검증기와 업로드 관찰/강제 모드, iOS 등록 저장소·원자적 counter 갱신, 양 플랫폼 앱 내부 카메라와 네이티브 증명·프론트엔드 연결을 구현했다. 서버 94개, Swift 핵심 8개, JS 흐름·브리지 79개 테스트를 통과했다. 확정 계약과 현재 구현 범위는 [docs/attestation.md](docs/attestation.md)를 따른다. 아래 내용은 초기 계획이다. Redis는 원자적 scope 확인·삭제를 사용하고, 업로드 client data는 서버가 재구성하며, 촬영은 양 플랫폼에서 앱 내부 CameraView를 사용한다. 실제 테이블은 [DDL](infra/sql/20260913-device-attestation.sql)을 배포 전에 적용해야 한다. iOS Xcode 빌드, 운영 배포와 양 플랫폼 실기기 검증은 후속 작업이며 현재 `ATTESTATION_ENFORCE=false`를 유지한다. Google Play의 앱 인식 판정만으로 모든 사이드로드를 구분할 수 있는 것은 아니며 현재 라이선스 판정은 강제하지 않는다.

## 배경

일부 사용자가 카메라로 촬영하지 않은 사진을 인증샷으로 업로드하고 있다. 미리 찍어둔 열람실 사진을 저장해 두었다가 예약 시간에 맞춰 올리는 방식이다. 업로드 시각이 지나치게 규칙적이어서 사람이 손으로 조작한 것이 아니라 자동화된 것으로 의심된다.

인증샷은 "예약한 방에 실제로 왔는지"를 확인하는 유일한 수단이다. 이것이 무력화되면 예약 시스템 전체가 의미를 잃는다.

## 현재 방어가 실패하는 이유

### Cloudflare Turnstile

Turnstile 토큰이 증명하는 것은 "사람이 챌린지를 통과했다" 하나뿐이다. 사진의 출처나 요청의 정당성은 증명하지 않는다.

site key(`frontend/src/shared/config/env.ts`)는 설계상 공개 값이므로 누구나 위젯을 렌더링해 토큰을 얻을 수 있다. Cloudflare 대시보드의 hostname allowlist가 제3자 사이트 임베드는 막지만, 공격자는 우리 도메인을 그대로 사용하면 된다. 특히 `https://v3.ssu.today/turnstile.html`은 브라우저로 직접 열 수 있고 `action`을 쿼리 파라미터로 받으므로, 원하는 action의 유효한 토큰을 얼마든지 발급받을 수 있다.

더 간단한 경로도 있다. 앱에서 정상적으로 촬영 흐름을 진행하면서 프록시로 요청을 가로채 `file` 파트만 교체하면, 토큰과 hostname과 action이 모두 진짜인 상태로 임의의 사진이 업로드된다.

서버 측 검증도 얕다. `TurnstileVerificationResponse`는 `success` 필드만 선언하고 있어 siteverify 응답의 `action`, `hostname`, `challenge_ts`를 전혀 확인하지 않는다.

### Gemini 자동 검사

`GeminiVerifyPhotoInspectionAdapter`의 프롬프트는 "이 사진이 스터디룸에서 찍혔는가"를 판정한다. 그런데 미리 찍어둔 사진은 실제 스터디룸 사진이므로 정직하게 통과한다.

내용 검사는 "어디서 찍혔는가"만 볼 수 있고 "언제 찍혔는가"는 알 수 없다. 이 수법에는 원리적으로 대응할 수 없다.

### 카메라 강제

`WebViewScreen.tsx`의 `camera.captureVerifyPhoto` 핸들러는 `ImagePicker.launchCameraAsync`만 사용하며 갤러리 선택 경로가 없다. 그러나 이 강제는 전적으로 클라이언트 측에서만 이루어진다. `POST /reserve/verifyPhoto/upload`는 multipart 요청을 받을 뿐이며, 서버는 그 바이트가 카메라에서 나왔는지 갤러리에서 나왔는지 판별할 수단이 없다.

## 공격 경로 정리

앱이 카메라만 허용하는데도 저장된 사진이 올라온다면 경로는 셋 중 하나다.

| 경로 | 필요 조건 | 현재 상태 |
| --- | --- | --- |
| API 직접 호출 / 프록시로 file 교체 | 프록시 도구 | 완전히 열려 있음. 가장 유력 |
| 가상 카메라 앱으로 카메라 피드 주입 | 루팅 | 열려 있음 |
| 다른 화면이나 인쇄물을 재촬영 | 준비물(모니터/프린터) | Gemini 프롬프트가 모아레로 일부 탐지 |

업로드 시각이 규칙적이라는 점을 감안하면 첫 번째 경로가 압도적으로 유력하다.

## 선택한 방향: App Attestation

플랫폼 사업자가 "변조되지 않은 정품 앱이 정상 기기에서 이 요청을 만들었다"를 보증하고, 서버가 그 보증을 검증한다. 앱에 비밀 값을 심지 않으므로 키 탈취로 우회할 수 없다.

- **iOS**: App Attest. 키쌍이 Secure Enclave 안에서 생성되며 개인키는 앱 메모리에도 나오지 않는다. Apple이 공개키를 인증서 체인으로 보증한다.
- **Android**: Play Integrity API. 판정에 서명하는 주체가 Google 서버다. 앱은 판정을 요청할 뿐이다.

핵심은 **요청 바인딩**이다. attestation에 `SHA-256(사진 바이트)`을 함께 묶으면 그 증명은 해당 사진 하나에만 유효하다. 프록시로 파일을 교체하는 순간 해시가 어긋나 서버가 거부한다.

### 대안 검토

**서버 발급 nonce를 장면에 포함시키기**를 함께 검토했으나 우선순위에서 내렸다.

앱이 촬영 후 이미지에 nonce를 합성하는 방식은 사용자가 직접 합성한 것과 구분되지 않으므로 무의미하다. 사용자가 nonce를 종이에 적어 함께 촬영하게 하는 방식은 디지털 합성은 막지만, 인쇄한 열람실 사진 옆에 nonce를 적은 쪽지를 놓고 찍으면 그대로 통과한다. 전원이 UX 비용을 치르는 데 비해 막는 범위가 부분적이다.

**Wi-Fi BSSID 대조**는 유효한 후속 과제로 남긴다. 서버가 열람실 AP의 BSSID 목록을 알고 앱이 연결된 네트워크의 BSSID를 함께 보내면, 열람실 안에 있어야만 통과하게 만들 수 있다. "실제로 그 방에 왔는가"라는 본래 목적에 가장 직접적으로 부합하며 UX 비용도 작다. BSSID 위조에는 루팅이 필요하므로 attestation과 결이 맞는다. attestation 적용 후에도 문제가 남으면 이쪽을 다음 단계로 진행한다.

### 막는 것과 못 막는 것

attestation 적용 후 루팅/탈옥 없이는 다음이 전부 차단된다.

- API 직접 호출 (attestation을 만들 수 없음)
- 프록시로 file 파트 교체 (사진 해시 불일치)
- 자동화 스크립트 (정품 앱 프로세스 밖)
- 앱 리패키징 및 Frida gadget 주입 (`appRecognitionVerdict` 불일치)
- 에뮬레이터 (`MEETS_DEVICE_INTEGRITY` 실패)
- 가상 카메라 앱, Play Integrity 우회 모듈 (루팅 필요)

다음은 여전히 가능하다.

- 인쇄물이나 다른 화면을 실제 카메라로 재촬영. 다만 프린터를 준비하고 인쇄물을 들고 다녀야 하므로 비용이 크게 오른다. Gemini 프롬프트에 인쇄물 흔적(종이 질감, 장면의 평면성, 가장자리, 조명 반사) 판정을 추가해 일부 보완한다.
- 다른 사람이 대신 촬영해 주는 경우. 이는 무결성 문제가 아니라 본인 확인 문제이며 기술로 해결할 수 없다.

---

## 구현 계획

### 설계 요약

기존 `TurnstileVerificationPort` 구조를 그대로 따른다.

```
ssutoday-common/ssutoday-core/.../port/AttestationVerificationPort.kt
ssutoday-common/ssutoday-adapter/.../attestation/AttestationVerificationAdapter.kt   (platform 분기)
ssutoday-common/ssutoday-adapter/.../attestation/AppAttestVerifier.kt                (iOS)
ssutoday-common/ssutoday-adapter/.../attestation/PlayIntegrityVerifier.kt            (Android)
```

설계 결정 세 가지.

1. 업로드 DTO의 신규 필드는 전부 nullable로 시작한다. 관찰 모드 동안 구버전 앱이 그대로 동작해야 한다. 강제 전환 시점에 강제 업데이트로 구버전을 밀어낸다.
2. challenge는 Redis `@RedisHash`로 저장한다. `RefreshToken` 패턴을 따르며 TTL 60초, 사용 시 삭제한다.
3. 관찰 모드 플래그는 `ssutoday.gemini.enforce` 패턴을 복사한다. 검증에 실패해도 업로드는 통과시키고 Discord에만 기록한다.

### Phase 0 — 사전 준비 (0.5d, 코드 없음)

- GCP 프로젝트 번호 확보, Play Integrity API 활성화
- 서비스 계정 생성 및 키 발급 → `PLAY_INTEGRITY_CREDENTIALS` 환경변수. `FIREBASE_CREDENTIALS`와 동일한 방식으로 주입
- Play Console 내부 테스트 트랙 세팅. 이것이 없으면 Android 검증을 테스트할 수 없다
- Apple Team ID 확인. rpId는 `<TeamID>.com.ssutoday`
- `.env.example`에 신규 키 추가

완료 조건: 서비스 계정으로 Play Integrity API를 호출했을 때 401이 아닌 응답이 온다.

### Phase 1 — 서버 (5.5d)

#### 1-1. challenge 발급 (0.5d)

```
ssutoday-domain/.../student/AttestChallenge.kt                @RedisHash, TTL 60s
ssutoday-api/.../attest/AttestController.kt                   POST /attest/challenge
ssutoday-application/.../attest/AttestApplicationService.kt
```

`@LoginStudent`로 인증된 요청만 받는다. 랜덤 32바이트를 Base64로 반환한다.

완료 조건: 호출할 때마다 다른 값이 나오고, 60초 후 소멸하며, 같은 값을 두 번 소모할 수 없다.

#### 1-2. 등록 저장소 (0.5d)

`ddl-auto: validate` 설정이므로 테이블을 수동으로 만들어야 한다.

```sql
CREATE TABLE device_attestation (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  INT            NOT NULL,
  platform    VARCHAR(10)    NOT NULL,
  key_id      VARCHAR(255)   NOT NULL,
  public_key  VARBINARY(255) NOT NULL,
  counter     BIGINT         NOT NULL DEFAULT 0,
  created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY device_attestation_key_id_uindex (key_id),
  KEY device_attestation_student_id_index (student_id)
);
```

학생당 여러 행을 허용해야 한다. 기기 교체나 앱 재설치 시 키가 새로 생성되기 때문이다.

`DeviceAttestation` 엔티티, repository, domain service를 추가한다. Android는 서버가 저장할 키가 없으므로 iOS만 이 테이블을 사용한다.

#### 1-3. port와 관찰 모드 플래그 (0.5d)

```yaml
ssutoday:
  attestation:
    enforce: false
    play-integrity:
      cloud-project-number: ${PLAY_CLOUD_PROJECT_NUMBER:}
      credentials: ${PLAY_INTEGRITY_CREDENTIALS:}
    app-attest:
      team-id: ${APPLE_TEAM_ID:}
      bundle-id: com.ssutoday
      production: ${APP_ATTEST_PRODUCTION:true}
```

#### 1-4. Android 검증기 (1d)

신규 의존성이 필요 없다. `com.google.firebase:firebase-admin`이 끌고 오는 `GoogleCredentials`로 OAuth 토큰을 발급받고, 기존 `RestClient`로 `decodeIntegrityToken`을 호출한다.

verdict에서 확인할 항목:

| 필드 | 요구값 |
| --- | --- |
| `requestDetails.requestPackageName` | `com.ssutoday` |
| `requestDetails.requestHash` | 서버 재계산값과 일치 |
| `requestDetails.timestampMillis` | 최근 N분 이내 |
| `appIntegrity.appRecognitionVerdict` | `PLAY_RECOGNIZED` |
| `appIntegrity.certificateSha256Digest` | 우리 서명 인증서 |
| `deviceIntegrity.deviceRecognitionVerdict` | `MEETS_DEVICE_INTEGRITY` 포함 |

완료 조건: 내부 테스트 트랙 앱에서 발급한 토큰은 통과하고, 직접 설치한 APK의 토큰은 `UNRECOGNIZED_VERSION`으로 거부된다.

#### 1-5. iOS 검증기 (2d) — 최대 리스크 구간

의존성 추가: `jackson-dataformat-cbor`, BouncyCastle(ASN.1 OID 파싱용).

**등록 검증** 단계:

1. CBOR 디코드 → `fmt == "apple-appattest"`, `attStmt.x5c`, `authData`
2. `x5c` 인증서 체인을 Apple App Attest Root CA로 검증
3. leaf 인증서의 OID `1.2.840.113635.100.8.2` 확장값이 `SHA256(authData ‖ clientDataHash)`와 일치하는지 확인
4. `authData`의 rpId 해시가 `SHA256("<TeamID>.com.ssutoday")`와 일치하는지 확인
5. `authData`의 counter가 0인지 확인
6. `authData`의 aaguid가 환경에 맞는지 확인 (`appattest` / `appattestdevelop`)
7. credentialId가 keyId와 일치하는지 확인
8. 통과하면 `authData`에서 P-256 공개키를 추출해 저장

**assertion 검증** 단계:

1. keyId로 저장된 공개키 조회
2. CBOR 디코드 → `signature`, `authenticatorData`
3. `nonce = SHA256(authenticatorData ‖ clientDataHash)`
4. 공개키로 signature가 nonce에 대한 유효한 ECDSA 서명인지 검증
5. `authenticatorData.counter`가 저장된 counter보다 큰지 확인하고 갱신한다. counter는 단조증가하므로 같은 assertion의 재사용이 차단된다
6. rpId 해시 재확인
7. challenge가 유효하고 미사용인지 확인 후 소모 처리
8. `clientData.photoSha256`이 서버가 직접 계산한 업로드 파일의 SHA-256과 일치하는지 확인
9. `clientData.reservationId`가 `request.idx`와 일치하는지 확인

두 단계를 별도 함수로 분리하고 각 검증 항목을 독립적으로 테스트할 수 있게 구성한다. 통으로 작성하면 실패 지점을 찾기 어렵다.

**착수 전 선행 작업**: Apple 공개 테스트 벡터로 단위 테스트를 먼저 작성한다. 실기기를 붙이기 전에 검증 로직의 정확성을 확보해야 반복 주기가 짧아진다. 여기에 반나절을 쓰는 것이 이틀을 아낀다.

완료 조건: 테스트 벡터가 전부 통과하고, 각 검증 단계를 의도적으로 깨뜨렸을 때 정확히 그 단계에서 실패한다.

#### 1-6. 업로드 통합 (1d)

`VerifyPhotoRequest`에 nullable 필드를 추가한다.

```kotlin
val platform: String?,
val challenge: String?,
val attestation: String?,
val clientData: String?,   // iOS만 사용
```

`VerifyPhotoApplicationService.upload()`에서 Turnstile 검증 직후에 다음을 수행한다.

1. `file.bytes`를 읽어 SHA-256을 계산한다. 사진이 1280px / quality 0.8로 압축되어 수백 KB 수준이므로 전체를 메모리에 올려도 무방하다
2. attestation을 검증한다. 실패 시 `enforce`가 켜져 있으면 예외를 던지고, 꺼져 있으면 Discord에 기록한 뒤 통과시킨다
3. `ByteArrayInputStream`으로 기존 업로드 흐름에 연결한다

`StatusCode`에 `SSU4206`을 추가하고 `messages.properties`에 메시지를 등록한다.

Phase 1 완료 조건: attestation 없이 기존 앱으로 업로드하면 여전히 성공하며, Discord에 attestation 누락 알림이 기록된다.

### Phase 2 — Android 네이티브 (1.5d)

Android를 먼저 하는 이유는 검증 반복 주기가 iOS보다 짧기 때문이다. 브리지 프로토콜 설계를 여기서 확정하고 iOS가 따라간다.

- `mobile/src/bridge/protocol.ts`에 `security.attest` 추가
- `frontend/src/shared/native/bridgeProtocol.ts`, `nativeBridge.ts` 동기화
- 네이티브 모듈에서 `prepareIntegrityToken` 워밍업과 `request` 호출 구현
- `camera.captureVerifyPhoto`가 SHA-256도 함께 반환하도록 확장한다. 네이티브가 이미 base64를 들고 있으므로 JS에서 해싱하는 것보다 저렴하다
- `frontend/src/features/reservation/api/reservationRepository.ts`의 `uploadVerifyPhoto` 수정
- `scripts/check-bridge-protocol-sync.js` 통과 필수

완료 조건: 내부 테스트 트랙 앱에서 업로드했을 때 서버 로그에 `PLAY_RECOGNIZED`, `MEETS_DEVICE_INTEGRITY`, requestHash 일치가 기록된다.

### Phase 3 — iOS 네이티브 (2d)

- `security.attestRegister` — 최초 1회, `keyId`와 `attestationObject` 반환
- `security.attest` — 매 업로드, assertion 반환

키 등록은 첫 `security.attest` 호출 시 keyId가 없으면 자동으로 선행하도록 구성한다. 브리지 왕복이 한 번 늘지만 사용자는 별도 절차를 인지하지 않는다.

`app.config.js`에 `newArchEnabled: true`가 설정되어 있으므로 TurboModule 규약을 확인해야 한다.

완료 조건: TestFlight 빌드에서 등록과 업로드가 정상 동작하고, 앱 재설치 후에도 재등록되어 계속 동작한다.

### Phase 4 — 관찰에서 강제로 (달력 기준 2~3주)

1. 양 스토어에 배포한다. 네이티브 모듈이 추가되므로 `expo-updates` OTA로는 나갈 수 없다. 버전을 올려 재배포해야 하며 iOS 심사 리드타임이 일정에 포함된다
2. `enforce: false` 상태로 1~2주 운영한다
3. 다음 데이터를 수집한다
   - attestation 미포함 요청 비율 (구버전 잔존율)
   - 플랫폼별 검증 실패율
   - 실패 사유 분포 (Play Services 부재 / 구형 기기 / 루팅 / 실제 공격)
4. 실패율이 납득 가능한 수준으로 내려가면 강제 업데이트를 푸시한 뒤 `enforce: true`로 전환한다

## 리스크

- **iOS attestation 검증이 단일 최대 리스크다.** 검증 단계가 많고 하나라도 틀리면 조용히 실패한다. 예상보다 두 배 걸릴 수 있다. Apple 공개 테스트 벡터 기반 단위 테스트를 선행하는 것으로 완화한다
- **개발 중 테스트 마찰.** Android는 Play Console 내부 테스트 트랙을 거쳐야 정상 판정이 나오고, iOS는 시뮬레이터를 쓸 수 없어 실기기와 TestFlight가 필수다. 실기기 디버깅에 별도로 2~3일을 잡아야 한다
- **Play Integrity 무료 쿼터 미확인.** 인증샷 업로드 빈도라면 여유로울 것으로 보이나 실측이 필요하다
- **구형 및 비표준 기기 배제 비율 미지수.** 관찰 모드 데이터가 나오기 전에는 정책을 확정할 수 없다

## 중단 기준

정상 사용자의 검증 실패율이 3%를 넘으면 강제 전환을 보류하고 원인부터 정리한다. 무임승차를 막으려다 정상 이용자를 막는 것이 더 큰 손해다. `VerifyPhotoApplicationService.inspect()` 주석에 명시된 기존 원칙과 동일하다.

## 되돌리기

전 구간에서 `ssutoday.attestation.enforce: false`가 킬 스위치다. 문제가 생기면 설정 한 줄로 즉시 무력화되며 업로드는 정상 동작한다. 스키마 변경도 nullable 컬럼 추가와 신규 테이블뿐이라 롤백 부담이 없다.

## 공수 합계

| 구분 | 공수 |
| --- | --- |
| 서버 | 5.5d |
| 모바일 | 4d |
| 인프라 및 계정 설정 | 0.5d |
| 실기기 및 스토어 디버깅 | 2~3d |
| 코딩 합계 | 12~13d |
| 스토어 심사와 관찰 모드 | 달력 기준 2~3주 추가 |

Phase 1-4(Android 검증기)와 1-5(iOS 검증기)는 서로 독립적이다. Phase 2는 1-4만 완료되면 시작할 수 있다.

## 별건으로 확인된 문제

`AdminReservationRequest.signature`가 서버에서 검증되지 않는다. `AdminReservationCommand`까지 전달되지만 `ReservationCommandApplicationService`에서 사용되지 않는다. 관리자 도구의 생체 인증이 현재 아무 역할도 하지 않으며, `student.isAdmin` 검사만 통과하면 임의의 서명 값으로 `/reserve/adminTools`를 호출할 수 있다. 이 계획과 별개로 처리해야 한다.

Turnstile siteverify 응답의 `action`과 `hostname`을 검증하지 않는 문제도 남아 있다. 이 공격을 막지는 못하지만 위생 차원에서 함께 정리할 가치가 있다. 함께 `frontend/public/turnstile.html`의 `action` 쿼리 파라미터를 고정값으로 바꿔 임의 action 토큰 발급 경로를 줄인다.
