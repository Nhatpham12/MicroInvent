-- tenant-service – dữ liệu mặc định

INSERT INTO tenants (code, name) VALUES ('tigernet', 'TigerNet');
INSERT INTO tenant_datasources (tenant_id, service_name, db_name)
SELECT t.id, s.service_name, CONCAT(s.service_name, '_', t.code)
FROM tenants t
CROSS JOIN (SELECT 'identity' AS service_name UNION ALL SELECT 'catalog' UNION ALL SELECT 'inventory'
            UNION ALL SELECT 'procurement' UNION ALL SELECT 'sales' UNION ALL SELECT 'rental'
            UNION ALL SELECT 'report' UNION ALL SELECT 'notification' UNION ALL SELECT 'file') s
WHERE t.code = 'tigernet';
