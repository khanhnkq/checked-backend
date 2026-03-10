package com.codegym.locketclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LocketCloneApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocketCloneApplication.class, args);
	}

}
