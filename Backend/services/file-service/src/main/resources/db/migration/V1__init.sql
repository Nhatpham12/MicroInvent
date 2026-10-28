-- file-service – schema khởi tạo (tách từ Docs/database/schema.sql)

CREATE TABLE file_objects (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  ref_type       VARCHAR(30),                              -- PRODUCT, RECEIPT, EXPORT_SLIP...
  ref_id         INT,
  original_name  VARCHAR(255) NOT NULL,
  content_type   VARCHAR(100) NOT NULL,
  size_bytes     BIGINT NOT NULL,
  storage_path   VARCHAR(500) NOT NULL,
  checksum       CHAR(64),                                 -- SHA-256
  uploaded_by    INT NOT NULL,
  is_deleted     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file_ref (ref_type, ref_id)
);
