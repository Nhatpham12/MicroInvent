package com.microinvent.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Đường dẫn không cần JWT (login, refresh, quên mật khẩu, health, Swagger...). */
@ConfigurationProperties(prefix = "microinvent.gateway")
public class GatewaySecurityProperties {

    private List<String> publicPaths = new ArrayList<>();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
