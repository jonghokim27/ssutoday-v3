# ssutoday

## 개발 철학

### 기본 원칙

- 코드베이스의 기존 패턴을 우선한다.
- 단순한 해결책을 선호하되, 장기 유지보수에 필요한 구조는 놓치지 않는다.
- 조건 분기가 길어질 때는 if/else 체인보다 얼리 리턴 형태를 우선한다.
- 기능 변경은 관련 테스트나 검증을 함께 고려한다.
- 문서화는 실제 작업에 도움이 되는 내용 중심으로 유지한다.

### 명명과 조회 규칙

- 조회 메서드 명명은 `get`은 없으면 안 되는 조회로 예외를 던지고, `find`는 없을 수 있는 조회로 `null` 또는 빈 컬렉션을 반환한다.
- 목록 조회 API는 offset 기반 페이징보다 keyset 기반 페이징을 우선한다.

### 아키텍처와 계층 책임

- 도메인, 모듈 경계, 계층 책임을 흐리지 않는 방향으로 수정한다.
- API 계층은 HTTP 표현, 요청/응답 매핑, 인증 사용자 주입처럼 표현 계층 책임만 가진다.
- application 계층은 use case와 비즈니스 흐름을 담당하며, presentation 계층에 반환하는 DTO는 application 계층에 둔다. (단, DTO는 웹 기술에 종속적이지 않아야 한다.)
- domain 계층은 엔티티, 도메인 객체, 개별 도메인 비즈니스 규칙을 담당한다.
- 재사용성은 `api`/`batch`/`consumer`에서 `application`, `domain`으로 올라갈수록 높아지도록 설계한다.

### 계층 호출과 도메인 서비스

- application service는 domain repository를 직접 사용하지 않고 domain service를 통해 도메인 로직을 호출한다.
- domain service는 자기 도메인의 repository만 호출한다.
- domain service는 다른 domain service를 호출하지 않는다.
- 계층 호출 방향은 항상 application service -> domain service -> repository로 유지한다.
- domain service는 가능하면 엔티티를 직접 반환하지 않고 `UserView`, `ProductView` 같은 도메인 객체를 반환한다.
- 엔티티를 도메인 객체로 변환하는 로직은 domain 계층의 도메인별 `factory` 디렉터리에 둔다.
- 엔티티는 상태 보관과 상태 변경을 담당하고, 상태 전이의 비즈니스 조건 검사는 domain/application service에서 수행한다.

### 트랜잭션과 캐시

- 트랜잭션 경계는 `@Transactional` 어노테이션을 통해 application 계층에서 관리한다.
- 조회 전용 use case에는 `@Transactional(readOnly = true)`를 적극 사용한다.
- 캐시 경계는 `@Cacheable`, `@CacheEvict`, `@CachePut` 어노테이션을 통해 domain 계층에서 관리한다.
- 캐시 무효화는 DB 접근 최소화를 우선해 전체 삭제보다 변경 영향이 있는 키나 prefix만 선별 삭제한다.
- DB 트랜잭션 성공 이후 외부 이벤트를 발행해야 하면 `waito-core`의 `afterCommit {}` 유틸을 사용한다.

### 외부 의존성과 포트

- 외부 라이브러리의 기능은 application/domain 계층에서 호출할 수 있다.
- 외부 라이브러리 기능을 호출 할 때는 구현체를 직접 의존하지 않고 `waito-core`의 port 인터페이스에만 의존한다.
- application/domain 계층은 Kafka, Redis 같은 인프라 구현체를 직접 알지 않고 `waito-core`의 port 인터페이스에 의존하며, 실제 구현은 adapter 모듈에서 제공한다.

### DTO와 공용 코드

- DTO는 하나의 파일에 몰아넣지 않고 `dto` 디렉터리 아래 클래스별 파일로 분리한다.
- DTO 변환용 factory method는 `factory` 디렉터리의 별도 파일로 분리한다.
- enum, 정규식처럼 프로젝트 공통적으로 활용될 수 있는 파일들은 개별 모듈에서 만들지 않고 `waito-core`의 공용 코드로 분리한다.
- 공통 에러 코드 메시지는 실행 모듈별로 흩어두지 않고 `waito-core`의 `messages.properties`에서 관리한다.
- 페이징 요청/응답처럼 API 전반에서 반복되는 DTO는 `waito-core` 공용 코드로 분리하고, 컨트롤러에서는 `@ModelAttribute`로 받는다.

### Repository 사용

- Spring Data JPA repository는 불필요한 재추상화를 하지 않고 정통적인 repository 인터페이스 방식으로 사용한다.

### 예외 처리

- 정상 비즈니스 흐름상 발생 가능한 실패는 `BusinessException`, 사용자 입력 형식 오류는 `InvalidInputException`으로 처리한다.
- 발생하면 안 되는 내부 불변식 위반이나 데이터 정합성 오류는 `BusinessException`으로 감추지 않고 `RuntimeException` 계열로 처리한다.
