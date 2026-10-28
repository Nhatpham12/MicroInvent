package com.microinvent.tenant.starter;

import com.zaxxer.hikari.HikariDataSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;

/**
 * Tạo một HikariDataSource cho mỗi tenant, chạy Flyway (db/migration) trên từng database
 * rồi gộp lại thành TenantRoutingDataSource. Service dùng starter này phải tắt spring.flyway.
 */
@AutoConfiguration(before = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
@ConditionalOnProperty(prefix = "microinvent.tenant", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantDataSourceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceAutoConfiguration.class);

    @Bean
    @Primary
    public DataSource dataSource(TenantProperties props) {
        Assert.hasText(props.getUrlTemplate(), "microinvent.tenant.url-template is required");
        Set<String> tenants = new LinkedHashSet<>(props.getTenants());
        tenants.add(props.getDefaultTenant());

        Map<Object, Object> targets = new LinkedHashMap<>();
        for (String tenant : tenants) {
            DataSource ds = createDataSource(props, tenant);
            migrate(props, tenant, ds);
            targets.put(tenant, ds);
        }
        TenantRoutingDataSource routing = new TenantRoutingDataSource(props.getDefaultTenant(), Set.copyOf(tenants));
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(targets.get(props.getDefaultTenant()));
        routing.afterPropertiesSet();
        return routing;
    }

    private static DataSource createDataSource(TenantProperties props, String tenant) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("tenant-" + tenant);
        ds.setJdbcUrl(String.format(props.getUrlTemplate(), tenant));
        ds.setUsername(props.getUsername());
        ds.setPassword(props.getPassword());
        ds.setMaximumPoolSize(props.getMaxPoolSize());
        return ds;
    }

    private static void migrate(TenantProperties props, String tenant, DataSource ds) {
        log.info("Flyway migrate tenant '{}'", tenant);
        Flyway.configure()
                .dataSource(ds)
                .locations(props.getMigrationLocations().toArray(String[]::new))
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
