package com.gurukul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GurukulApplication {

	public static void main(String[] args) {
		SpringApplication.run(GurukulApplication.class, args);
	}

}
