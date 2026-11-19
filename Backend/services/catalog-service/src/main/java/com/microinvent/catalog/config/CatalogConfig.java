package com.microinvent.catalog.config;

import com.microinvent.catalog.security.FieldEncryptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfig {

    /** Thiếu hoặc sai khóa thì service không khởi động (không có khóa mặc định ngoài profile dev). */
    @Bean
    public FieldEncryptor fieldEncryptor(CatalogProperties props) {
        return new FieldEncryptor(props.encryptionKey());
    }
}
