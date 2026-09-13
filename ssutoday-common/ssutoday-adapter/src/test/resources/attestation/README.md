# Apple 검증 예제

`apple-guide-attestation.b64`는 Apple의 [Attestation Object Validation Guide](https://developer.apple.com/documentation/devicecheck/attestation-object-validation-guide)에 공개된 2026년 예제 데이터다 (확인: 2026-09-13). 실제 사용자나 서비스 계정의 비밀이 아니다.

예제 인증서 유효 기간에 시계를 고정해 Apple Root CA 체인을 확인한다. 이 예제의 인증서 nonce는 `SHA256(authData || challenge 원문)`이며 문서의 `SHA256(authData || SHA256(challenge))`와 다르다. 따라서 서버의 명세 검증은 `NONCE_MISMATCH`로 거부하는 것이 테스트 기대값이다. 이 차이에 맞춰 운영 검증을 완화하지 않는다. 예제의 확장에는 OS 실행 파일 category 1도 들어 있으며 SSUTODAY의 TestFlight/App Store 허용 정책과 다르다.

정상 등록·assertion 및 단계별 변조 테스트는 테스트 중 생성한 P-256 키와 별도의 테스트 CA를 사용한다. 운영 trust anchor는 Apple Root CA로 고정되어 있다.
