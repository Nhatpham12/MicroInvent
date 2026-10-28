package com.microinvent.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ProcurementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcurementServiceApplication.class, args);
	}

}
