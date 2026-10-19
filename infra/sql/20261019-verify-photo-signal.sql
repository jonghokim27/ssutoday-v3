-- 서버 배포 전에 적용한다. 인증샷 업로드별 검토 신호이며 업로드 처리 결과에는 관여하지 않는다.
-- 인증샷이 삭제되면 같은 예약에 다시 올릴 수 있으므로 업로드마다 행을 남긴다.
-- flags는 공백 없이 쉼표로 이어 FIND_IN_SET으로 조회한다. 신호가 없으면 빈 문자열이다.
CREATE TABLE verify_photo_signal (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    student_id INT NOT NULL,
    received_at DATETIME(3) NOT NULL,
    turnstile_solved_at DATETIME(3) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    verdict VARCHAR(40) NOT NULL,
    photo_url VARCHAR(255) NOT NULL,
    apps_detected VARCHAR(512) NULL,
    play_protect_verdict VARCHAR(32) NULL,
    device_activity_level VARCHAR(32) NULL,
    strong_integrity BIT(1) NULL,
    app_licensing_verdict VARCHAR(32) NULL,
    flags VARCHAR(255) NOT NULL DEFAULT '',
    KEY verify_photo_signal_reservation_index (reservation_id),
    KEY verify_photo_signal_student_received_index (student_id, received_at)
);
