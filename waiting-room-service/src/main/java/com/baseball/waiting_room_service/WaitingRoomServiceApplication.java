package com.baseball.waiting_room_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WaitingRoomServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WaitingRoomServiceApplication.class, args);
	}

}
