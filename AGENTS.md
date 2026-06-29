# ssutoday

## 적용 범위

- 이 문서는 저장소 루트와 서버 프로젝트 전체에 적용한다.
- 서버 프로젝트는 `ssutoday-api`, `ssutoday-application`, `ssutoday-domain`, `ssutoday-common:ssutoday-core`, `ssutoday-common:ssutoday-adapter`, `ssutoday-batch`, `ssutoday-consumer`로 구성된다.
- 프론트엔드(`frontend`)와 모바일 래퍼 앱(`mobile`)의 구조와 실행 방법은 루트 `README.md`를 우선 참고한다.

## 개발 철학

### 기본 원칙

- 코드베이스의 기존 패턴을 우선한다.
- 단순한 해결책을 선호하되, 장기 유지보수에 필요한 구조는 놓치지 않는다.
- 조건 분기가 길어질 때는 if/else 체인보다 얼리 리턴 형태를 우선한다.
- 기능 변경은 관련 테스트나 검증을 함께 고려한다.
- 문서화는 실제 작업에 도움이 되는 내용 중심으로 유지한다.
- 서버 계층 경계를 흐리는 빠른 우회보다, 현재 멀티 모듈 구조 안에서 책임을 분리하는 수정을 우선한다.

### 명명과 조회 규칙

- 조회 메서드 명명은 `get`은 없으면 안 되는 조회로 예외를 던지고, `find`는 없을 수 있는 조회로 `null` 또는 빈 컬렉션을 반환한다.
- 목록 조회 API는 offset 기반 페이징보다 keyset 기반 페이징을 우선한다.
- command 성격의 메서드는 `create`, `update`, `cancel`, `complete`, `register`, `unregister`, `execute`처럼 행위를 드러내는 이름을 사용한다.
- application DTO 이름은 `Command`, `Query`, `Result`, `Detail`, `Summary`처럼 use case에서의 역할이 드러나게 둔다.

## 서버 아키텍처

### 모듈 책임

- `ssutoday-api`
  - HTTP API, 컨트롤러, 요청/응답 DTO, 인증 필터, `@LoginStudent` 주입, 공통 응답 래핑을 담당한다.
  - `ArticleController`, `ReservationController`, `RoomController`, `StudentController`, `DeviceController`, `SsoController`, `AdminActionController`가 진입점이다.
  - application/domain/core에 의존하고, adapter는 runtimeOnly로만 끼운다.
- `ssutoday-application`
  - 기능별 use case, 트랜잭션 경계, 외부 이벤트 발행 시점, presentation으로 반환할 웹 비종속 DTO를 담당한다.
  - `*ApplicationService`는 `@Transactional` 경계를 가진다.
  - `article`, `device`, `push`, `reservation`, `room`, `sso`, `student` 패키지로 나뉜다.
- `ssutoday-domain`
  - JPA/Redis 엔티티, repository, domain service, 정책 객체, domain view, entity-view factory를 담당한다.
  - 도메인은 `article`, `config`, `reservation`, `room`, `sso`, `student`로 분리한다.
- `ssutoday-common:ssutoday-core`
  - 공통 예외, 상태 코드, 메시지, 외부 의존성 port, `afterCommit {}` 유틸, 공통 DTO를 둔다.
  - core는 인프라 구현체를 몰라야 한다.
- `ssutoday-common:ssutoday-adapter`
  - core port의 구현체를 둔다.
  - Kafka publisher, Firebase push sender, Cloudflare R2 storage, JWT, uSaint 인증, Turnstile/reCAPTCHA 검증, Discord webhook 알림 구현이 여기에 속한다.
- `ssutoday-batch`
  - 스케줄 기반 작업 진입점이다.
  - 공지 크롤링(`ArticleCrawlJob`), 인증샷 미촬영 취소(`ReservationPhotoCheckJob`), 예약 시작/종료/인증샷 알림(`ReservationNotificationJob`)을 실행한다.
- `ssutoday-consumer`
  - Kafka consumer 진입점이다.
  - 예약 요청 처리와 푸시 메시지 발송을 application service로 위임한다.

### 계층 호출 방향

- 계층 호출 방향은 항상 `api/batch/consumer -> application -> domain -> repository`로 유지한다.
- application service는 domain repository를 직접 사용하지 않고 domain service를 통해 도메인 로직을 호출한다.
- domain service는 자기 도메인의 repository만 호출한다.
- domain service는 다른 domain service를 호출하지 않는다.
- adapter 구현체는 core port를 구현하며, application/domain이 adapter 구현체 타입을 직접 알면 안 된다.
- api/batch/consumer는 adapter 구현체를 직접 호출하지 않는다. 외부 기능이 필요하면 application/core port 흐름을 사용한다.

## 계층별 작성 규칙

### API 계층

- 컨트롤러는 HTTP 표현, 요청/응답 매핑, 인증 사용자 주입, status annotation만 담당한다.
- 컨트롤러에서 도메인 정책을 판단하지 않는다. 판단은 application/domain 계층으로 내린다.
- 인증 사용자는 `@LoginStudent`와 `LoginStudentArgumentResolver`를 통해 받는다.
- 쿠키 기반 인증 처리는 `AuthenticationFilter`와 `TokenCookieWriter` 흐름을 따른다.
- 요청/응답 DTO는 기능별 `dto` 디렉터리에 클래스별 파일로 둔다.
- 공통 응답은 `ApiResponse`, `ApiResponseWriter`, `GlobalControllerAdvice`, `ResponseStatus` 패턴을 따른다.
- 사용자 입력 형식 오류는 `InvalidInputException` 또는 validation 예외 흐름으로 처리하고, 정상 비즈니스 실패는 `BusinessException`으로 표현한다.

### Application 계층

- application service는 use case 단위로 작성하고 `*ApplicationService`로 명명한다.
- 트랜잭션 경계는 application service의 `@Transactional`로 관리한다.
- 조회 전용 use case에는 `@Transactional(readOnly = true)`를 적극 사용한다.
- DB 트랜잭션 성공 이후 Kafka/Discord/push 같은 외부 이벤트를 발행해야 하면 `ssutoday-core`의 `afterCommit {}` 유틸을 사용한다.
- presentation 계층에 반환하는 DTO는 application 계층에 둘 수 있지만 웹 기술에 종속되면 안 된다.
- application DTO는 하나의 파일에 몰아넣지 않고 기능별 `dto` 디렉터리 아래 클래스별 파일로 분리한다.
- 외부 라이브러리 기능을 호출해야 하면 구현체가 아니라 `ssutoday-core`의 port 인터페이스에 의존한다.

### Domain 계층

- domain 계층은 엔티티, 도메인 객체, 개별 도메인 비즈니스 규칙, repository, policy를 담당한다.
- 엔티티는 상태 보관과 상태 변경 메서드만 담당한다.
- 상태 전이의 비즈니스 조건 검사는 domain/application service 또는 policy에서 수행한다.
- domain service는 가능하면 엔티티를 직접 반환하지 않고 `ArticleView`, `ReservationView`, `RoomView`, `StudentView` 같은 domain view를 반환한다.
- 엔티티를 domain view로 변환하는 로직은 도메인별 `factory` 디렉터리에 둔다.
- Spring Data JPA repository는 불필요한 재추상화를 하지 않고 정통적인 repository 인터페이스 방식으로 사용한다.
- Redis repository(`CrudRepository`)와 JPA repository는 domain에 둘 수 있지만 인프라 클라이언트 세부 구현은 adapter로 보낸다.

### Core와 Adapter

- enum, 정규식, 상태 코드, 공통 메시지, port처럼 프로젝트 공통으로 활용되는 코드는 개별 실행 모듈에 흩어두지 않고 `ssutoday-common:ssutoday-core`로 올린다.
- 공통 에러 코드 메시지는 실행 모듈별로 흩어두지 않고 core의 `messages.properties`에서 관리한다.
- adapter는 외부 서비스별 패키지로 분리한다.
  - `auth`: uSaint 인증
  - `discord`: Discord webhook appender와 예약/인증샷 알림
  - `kafka`: 예약 요청/푸시 메시지 publisher
  - `push`: Firebase push sender
  - `recaptcha`, `turnstile`: 보안 검증 adapter
  - `security`: JWT token adapter
  - `storage`: Cloudflare R2 storage adapter
- adapter 추가 시 먼저 core port를 정의하고 application/domain은 port에만 의존하게 만든다.

### Batch와 Consumer

- batch job은 스케줄과 실행 흐름만 갖고, 실제 저장/도메인 판단은 application/domain service로 위임한다.
- 공지 크롤러는 `crawler/base`의 공통 파서/크롤러 추상화와 `crawler/dto`를 우선 재사용한다.
- Kafka consumer는 메시지 역직렬화와 application service 호출만 담당한다.
- consumer에서 domain repository를 직접 호출하지 않는다.
- 푸시 발송은 `PushMessageApplicationService`와 `PushMessageSender` port 흐름을 유지한다.

## 트랜잭션, 캐시, 이벤트

- 트랜잭션 경계는 `@Transactional` 어노테이션을 통해 application 계층에서 관리한다.
- 조회 전용 use case에는 `@Transactional(readOnly = true)`를 사용한다.
- 캐시 경계가 필요하면 `@Cacheable`, `@CacheEvict`, `@CachePut` 어노테이션을 통해 domain 계층에서 관리한다.
- 캐시 무효화는 DB 접근 최소화를 우선해 전체 삭제보다 변경 영향이 있는 키나 prefix만 선별 삭제한다.
- DB 트랜잭션 성공 이후 외부 이벤트를 발행해야 하면 `afterCommit {}`를 사용한다.

## 예외 처리

- 정상 비즈니스 흐름상 발생 가능한 실패는 `BusinessException`으로 처리한다.
- 사용자 입력 형식 오류는 `InvalidInputException` 또는 Spring validation 예외 흐름으로 처리한다.
- 발생하면 안 되는 내부 불변식 위반이나 데이터 정합성 오류는 `BusinessException`으로 감추지 않고 `RuntimeException` 계열로 처리한다.
- 새 에러 코드를 추가하면 `StatusCode`와 `messages.properties`, API 응답 계약 테스트를 함께 확인한다.

## 설정과 환경 변수

- 실행 모듈의 `application.yml`은 각 모듈 고유 설정만 두고 공통 설정은 core의 `common.yml`로 가져온다.
- DB, Redis, Kafka, JWT, Turnstile, R2, Discord, Firebase 관련 값은 `.env.example`과 `common.yml`을 함께 확인한다.
- 민감 정보는 저장소에 커밋하지 않는다. `firebase.json`, `.env`, Google service 파일은 로컬/배포 환경에서 주입한다.
- Docker 실행 설정은 `infra/docker-compose.yml`과 루트 `Dockerfile`을 기준으로 유지한다.

## 테스트와 검증

- 변경 범위에 맞는 테스트를 함께 고려한다.
- 도메인 정책 변경은 `ssutoday-domain/src/test`의 정책 테스트를 우선 갱신한다.
- API 응답 형식 변경은 `ApiResponseContractTest`를 확인한다.
- 인증 사용자 주입 변경은 `LoginStudentArgumentResolverTest`를 확인한다.
- 크롤러 날짜 파싱/크롤링 흐름 변경은 batch 테스트를 확인한다.
- adapter 변경은 해당 adapter 테스트를 확인한다.
- 전체 검증은 `./gradlew build` 또는 Windows에서 `.\gradlew.bat build`를 기준으로 한다.
