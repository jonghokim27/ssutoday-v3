-- 서버 배포 전에 적용한다. 예약 요청별 검토 신호이며 요청 처리 결과에는 관여하지 않는다.
-- flags는 공백 없이 쉼표로 이어 FIND_IN_SET으로 조회한다. 신호가 없으면 빈 문자열이다.
CREATE TABLE reservation_request_signal (
    request_id BIGINT NOT NULL PRIMARY KEY,
    student_id INT NOT NULL,
    received_at DATETIME(3) NOT NULL,
    open_offset_millis BIGINT NOT NULL,
    turnstile_solved_at DATETIME(3) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    verdict VARCHAR(40) NOT NULL,
    apps_detected VARCHAR(512) NULL,
    play_protect_verdict VARCHAR(32) NULL,
    device_activity_level VARCHAR(32) NULL,
    strong_integrity BIT(1) NULL,
    app_licensing_verdict VARCHAR(32) NULL,
    flags VARCHAR(255) NOT NULL DEFAULT '',
    KEY reservation_request_signal_student_received_index (student_id, received_at),
    KEY reservation_request_signal_received_index (received_at)
);
