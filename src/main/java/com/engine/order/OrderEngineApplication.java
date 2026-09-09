package com.engine.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderEngineApplication.class, args);
    }
}
