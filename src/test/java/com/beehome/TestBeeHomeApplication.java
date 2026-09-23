package com.beehome;

import org.springframework.boot.SpringApplication;

public class TestBeeHomeApplication {

	public static void main(String[] args) {
		SpringApplication.from(BeeHomeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
