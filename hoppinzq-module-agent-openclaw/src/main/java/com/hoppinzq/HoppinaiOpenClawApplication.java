package com.hoppinzq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HoppinaiOpenClawApplication {
    public static void main(String[] args) {
        SpringApplication.run(HoppinaiOpenClawApplication.class, args);
    }
}
