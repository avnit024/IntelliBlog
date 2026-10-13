package com.intelliblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelliBlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntelliBlogApplication.class, args);
    }
}
