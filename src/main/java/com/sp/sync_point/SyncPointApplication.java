package com.sp.sync_point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry

public class SyncPointApplication {
	public static void main(String[] args) {
		SpringApplication.run(SyncPointApplication.class, args);
	}
}