-- tenant-service – schema khởi tạo (tách từ Docs/database/schema.sql)

-- tenant_db: danh sách tenant (doanh nghiệp) và database cấp cho từng service
CREATE TABLE tenants (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(30)  NOT NULL UNIQUE,              -- dùng trong header X-Tenant-Id và tên database
  name          VARCHAR(255) NOT NULL,
  tax_code      VARCHAR(20),
  contact_email VARCHAR(255),
  contact_phone VARCHAR(20),
  status        ENUM('ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE tenant_datasources (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     INT NOT NULL,
  service_name  VARCHAR(50)  NOT NULL,                     -- identity, catalog, inventory...
  db_name       VARCHAR(100) NOT NULL,                     -- vd catalog_tigernet
  status        ENUM('PROVISIONING','READY','FAILED') NOT NULL DEFAULT 'READY',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_service (tenant_id, service_name),
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);
