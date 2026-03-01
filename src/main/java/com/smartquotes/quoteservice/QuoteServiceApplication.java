package com.smartquotes.quoteservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuoteServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuoteServiceApplication.class, args);
	}

	@Bean
	CommandLineRunner checkVault(Environment env) {
		return args -> {
			System.out.println("=== Vault Debug ===");
			System.out.println("VAULT USER: " + env.getProperty("spring.datasource.username"));
			System.out.println("VAULT PASS: " + env.getProperty("spring.datasource.password"));
			System.out.println("VAULT URL:  " + env.getProperty("spring.datasource.url"));
			System.out.println("===================");
		};
	}

}
