package com.project.trackdonation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class TrackDonationApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrackDonationApplication.class, args);
	}

}
