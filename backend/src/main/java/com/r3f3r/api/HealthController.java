package com.r3f3r.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    HealthResponse health() {
        return new HealthResponse("UP");
    }

    record HealthResponse(String status) {
    }
}
