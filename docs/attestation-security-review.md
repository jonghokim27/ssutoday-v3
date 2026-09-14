# Attestation 배포 전 점검 — 2026-09-14

## 비밀키 검사

Gitleaks 8.30.1 공식 배포 파일의 SHA-256을 확인하고, 현재 추적 소스와 로컬 Git 전체 이력을 `--redact`로 검사했다. 현재 소스 탐지 2건과 과거 이력 탐지 5건은 아래와 같이 분류했다. 검사 범위에서 공격자가 서비스 인증에 사용할 수 있는 실제 비밀키는 발견하지 못했다.

| 탐지 | 확인 결과 |
| --- | --- |
| `.env.example`의 Discord 환경변수 | 값이 빈 줄인데 다음 환경변수 이름까지 잡힌 오탐. 과거 2개 커밋도 동일 |
| `PlayIntegrityAccessTokenProviderTest` | 테스트 실행 시 `KeyPairGenerator`로 생성한 키를 PEM 문자열로 만드는 코드. 고정 개인키 없음 |
| 과거 `frontend/.../env.ts` | 클라이언트에서 사용하도록 공개하는 reCAPTCHA **site key**. secret key 아님 |

GCP 프로젝트 번호, Apple Team ID, 앱 ID, 서명 인증서 SHA-256, Apple Root CA는 공개 식별자/공개키 자료다. 이 값만으로 증명 서명이나 서비스 계정 인증을 할 수 없다. 모바일에 서버용 공유 비밀키를 넣지 않는다. iOS의 App Attest 개인키는 DeviceCheck가 관리하고 서버에는 검증된 공개키만 저장한다.

서비스 계정 JSON, Firebase Admin JSON, `.env`, APK 서명키, 비밀번호와 Apple 자격 증명은 Git에서 제외한다. `.secrets/`는 Docker context에서도 제외하고 API에만 읽기 전용으로 마운트한다. 모바일 Google 설정 파일의 ignore도 보완했다. APK 변환용 자격 증명과 원시 스캔 보고서는 ignored 로컬 경로에만 보관한다. EAS에 저장된 실제 Google 앱 설정을 빌드에 사용하고 로컬 테스트 fixture를 배포하지 않는다.

## 서버 검증 재검토

| 항목 | 확인 내용 |
| --- | --- |
| 입력 바인딩 | 로그인 학생은 서버 principal에서 가져온다. 예약의 방·날짜·시작/종료 블록과 실제 업로드 사진 바이트로 서버가 해시를 재구성 |
| challenge | CSPRNG 32바이트, TTL 60초, 학생·용도·예약 scope 일치 후 Redis Lua로 원자적 한 번 소모. 다른 용도와 재사용 거부 |
| Android | Google 서버 decode, package/requestHash/timestamp/앱 인식/인증서 허용 목록/기기 무결성 검사. 클라이언트의 판정 JSON을 신뢰하지 않음 |
| iOS 등록 | 고정 Apple Root CA와 인증서 체인/기간, nonce, P-256 공개키와 keyId, COSE 좌표, App ID, 환경, 등록 counter 검사 |
| iOS assertion | 저장된 키의 소유자·환경, App ID, ECDSA 서명, 단조 counter 검사. 예약·사진 공통 키의 counter를 조건부 DB UPDATE로 갱신 |
| 관찰/강제 | 기본 `ATTESTATION_ENFORCE=false`: 구버전/실패 허용과 Discord 판정. `true`: 성공만 허용. 새 요청 필드에 필수 validation을 추가하지 않음 |
| 외부 알림 | DB 커밋 이후 발행, Discord에 증명/키 원문을 넣지 않음. 알림 오류가 이미 접수된 요청을 거부하지 않음 |
| 브리지 | 신뢰하는 HTTPS 메인 문서와 네이티브 난수 확인. 사진 URI·임의 해시 서명을 노출하지 않고 네이티브가 보관한 촬영 바이트에 귀속 |

서버 106개 테스트가 모두 통과했고 Redis/MySQL 컨테이너 테스트의 skip은 없었다. HTTP 구버전 계약, 변조·만료·재사용, 동시에 challenge/counter를 소모하는 요청, 예약/사진 양 모드와 저장 전 거부를 검사했다. Swift 10개, Android 6개, JS 91개 테스트 및 프론트엔드 빌드·모바일 타입 검사도 통과했다.

## 배포 범위와 남은 실환경 확인

코드·합성 증명·컨테이너 테스트 결과이며 운영 서버의 실제 Play/TestFlight 증명 통과를 대신하지 않는다. Apple 공개 예제 nonce의 문서 차이는 [fixture 설명](../ssutoday-common/ssutoday-adapter/src/test/resources/attestation/README.md)에 기록했고, 예제만 통과시키려고 검증을 완화하지 않았다.

`false`는 차단을 보장하는 모드가 아니다. 요청을 허용하면서 판정을 관찰하는 배포 단계다. 새 바이너리·프론트엔드·서버 배포 후 정상 예약/사진이 `VERIFIED`인지, 구버전 요청이 허용되는지 확인한 뒤 강제 전환한다. `true`에서는 구버전 앱도 거부하므로 전환 시 버전 정책을 함께 운영한다.

Google Play 라이선스 판정, App Attest receipt 기반 위험 분석과 전체 관리자 인증 체계 개편은 이번 범위에 포함하지 않는다. attestation은 실제 방 방문, 타인의 대리 촬영이나 화면/인쇄물 재촬영 자체를 증명하지 않는다. 일반 예약 API와 인증샷 API에 적용하며 기존 관리자 전용 예약 생성 경로는 그대로다.

운영 배포 전 [DDL](../infra/sql/20260913-device-attestation.sql)을 먼저 적용해야 한다. 운영 DB 변경과 서버 배포는 이번 PR 준비 작업에서 실행하지 않았다. 설정·프로토콜·배포 순서는 [attestation 계약](attestation.md)을 따른다.
