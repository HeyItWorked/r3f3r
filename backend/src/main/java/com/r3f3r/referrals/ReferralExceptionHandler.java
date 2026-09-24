package com.r3f3r.referrals;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ReferralExceptionHandler {

    @ExceptionHandler({ReferralNotFoundException.class, ProviderNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
        return ResponseEntity.status(404).body(Utils.map("message", e.getMessage(), "fieldErrors", Map.of()));
        // return ResponseEntity.notFound().build();
    }

    // bad json / bad id in the url
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> badInput(Exception e) {
        return ResponseEntity.status(400).body(Utils.map("message", "Malformed request", "fieldErrors", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        // Swallow database details: only a generic message is sent to clients.
        e.printStackTrace();
        return ResponseEntity.status(500).body(Utils.map("message", "Unexpected error", "fieldErrors", Map.of()));
    }
}

class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(Long id) {
        super("Provider not found: " + id);
    }
}
