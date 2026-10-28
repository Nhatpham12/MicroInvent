package com.microinvent.tenant.starter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình tenant cho một service. Mỗi tenant có database riêng: url-template với %s = mã tenant,
 * ví dụ jdbc:mysql://localhost:3306/catalog_%s.
 */
@ConfigurationProperties(prefix = "microinvent.tenant")
public class TenantProperties {

    private boolean enabled = true;
    private String defaultTenant = "tigernet";
    private List<String> tenants = new ArrayList<>(List.of("tigernet"));
    private String urlTemplate;
    private String username;
    private String password;
    private int maxPoolSize = 5;
    private List<String> migrationLocations = new ArrayList<>(List.of("classpath:db/migration"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public List<String> getTenants() {
        return tenants;
    }

    public void setTenants(List<String> tenants) {
        this.tenants = tenants;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public List<String> getMigrationLocations() {
        return migrationLocations;
    }

    public void setMigrationLocations(List<String> migrationLocations) {
        this.migrationLocations = migrationLocations;
    }
}
