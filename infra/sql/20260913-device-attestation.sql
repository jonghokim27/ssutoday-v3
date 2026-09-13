-- 서버 배포 전에 적용한다. Base64 key_id의 대소문자를 구분해야 한다.
CREATE TABLE device_attestation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    key_id VARCHAR(44) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    public_key VARBINARY(255) NOT NULL,
    production BIT(1) NOT NULL,
    registration_hash VARBINARY(32) NOT NULL,
    counter BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY device_attestation_key_id_uindex (key_id),
    KEY device_attestation_student_id_index (student_id)
);
