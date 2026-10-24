-- 서버 배포 전에 적용한다. 예약 1건에 대해 부여한 페널티이며 취소해도 지우지 않고 revoked_at을 남긴다.
-- type은 PHOTO(인증샷 미촬영·미인정) 또는 FRAUD(부정 예약), reason은 PenaltyReason 이름이다.
-- 유효한 페널티는 revoked_at IS NULL AND expires_at > NOW()이다.
CREATE TABLE penalty (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    reservation_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    granted_by VARCHAR(30) NOT NULL,
    granted_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    admin_token VARCHAR(220) NOT NULL,
    revoked_at DATETIME(3) NULL,
    revoked_by VARCHAR(30) NULL,
    revoke_reason VARCHAR(200) NULL,
    UNIQUE KEY penalty_admin_token_unique (admin_token),
    KEY penalty_student_expires_index (student_id, expires_at),
    KEY penalty_reservation_index (reservation_id)
);
