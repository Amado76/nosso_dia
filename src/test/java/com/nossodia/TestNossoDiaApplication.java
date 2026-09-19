package com.nossodia;

import org.springframework.boot.SpringApplication;

public class TestNossoDiaApplication {

	public static void main(String[] args) {
		SpringApplication.from(NossoDiaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
