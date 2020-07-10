package com.yapily.jose.database.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Add scan base to make sure we load the beans fro mthe JOSE-database
@SpringBootApplication(scanBasePackages = "com.yapily.jose.database")
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
