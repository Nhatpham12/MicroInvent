package com.microinvent.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eureka.client.enabled=false",
		"microinvent.jwt.secret=gateway-context-test-secret-gateway-context-test"})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
