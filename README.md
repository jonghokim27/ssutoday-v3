# SSUtoday backend

기존 Java Spring 서버, Python 크롤러, Node.js 푸시 전송기를 하나의 Kotlin/Spring Boot 멀티모듈 프로젝트로 통합한 프로젝트다.

## 모듈

- `ssutoday-api`: HTTP API와 인증 필터
- `ssutoday-application`: 기능별 유스케이스와 트랜잭션 경계
- `ssutoday-domain`: JPA/Redis 도메인 모델, 저장소, 도메인 정책
- `ssutoday-common:ssutoday-core`: 예외와 외부 연동 포트
- `ssutoday-common:ssutoday-adapter`: Kafka, JWT, S3, uSaint 어댑터
- `ssutoday-common:ssutoday-mvc`: 공통 API 응답과 예외 처리
- `ssutoday-batch`: 8개 공지 사이트 크롤링 진입점
- `ssutoday-consumer`: 예약 요청과 푸시 메시지 Kafka 소비 진입점

의존 방향은 `api/batch/consumer → application → domain → core`이며 외부 시스템 구현은 `adapter`가 담당한다. 도메인 엔티티는 API DTO를 참조하지 않는다.

코드 구성 규칙:

- 클래스, 인터페이스, enum은 파일당 하나만 선언한다.
- 요청, 응답, 명령, 조회, 결과 객체는 각 기능의 `dto` 패키지에 둔다.
- 애플리케이션 계층의 Spring 서비스는 `*Service`로 명명한다.

## 요구 환경

- JDK 21
- MySQL, Redis, Kafka
- Firebase 서비스 계정 파일(`firebase.json`)

## 빌드

```bash
./gradlew build
```

## 실행

```bash
./gradlew :ssutoday-api:bootRun
./gradlew :ssutoday-batch:bootRun
./gradlew :ssutoday-consumer:bootRun
```

Docker 실행 전 `.env.example`을 `.env`로 복사하고 값을 설정한다.

```bash
docker compose -f infra/docker-compose.yml up --build
```
