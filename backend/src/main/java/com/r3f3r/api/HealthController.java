package com.r3f3r.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class HealthController {

    // health check (http://localhost:8080/api/health)
    @GetMapping("/health")
    HealthResponse health() {
        System.out.println("health check called");
        // TODO check the db too
        return new HealthResponse("UP");
    }

    record HealthResponse(String status) {
    }
}
