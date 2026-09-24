package com.r3f3r;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class R3f3rApplication {

    public static void main(String[] args) {
        SpringApplication.run(R3f3rApplication.class, args);
        System.out.println("=================================");
        System.out.println("Server started on http://localhost:8080 !!!");
        System.out.println("=================================");
    }
}
