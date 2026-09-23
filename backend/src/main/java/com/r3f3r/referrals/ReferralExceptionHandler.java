package com.r3f3r.referrals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ReferralExceptionHandler {

    @ExceptionHandler(ReferralNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ReferralNotFoundException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("fieldErrors", Map.of());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        // Swallow database details: only a generic message is sent to clients.
        e.printStackTrace();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Unexpected error");
        body.put("fieldErrors", Map.of());
        return ResponseEntity.status(500).body(body);
    }
}
