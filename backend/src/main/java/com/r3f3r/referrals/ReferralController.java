package com.r3f3r.referrals;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class ReferralController {

    private final ReferralRepository repo;

    ReferralController(ReferralRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/referrals")
    List<ReferralResponse> all() {
        LocalDate today = LocalDate.now();
        return repo.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(r -> toResponse(r, today))
                .collect(Collectors.toList());
    }

    @PostMapping("/referrals")
    ResponseEntity<Map<String, Object>> create(@RequestBody ReferralRequest request) {
        Map<String, String> errors = validateCreate(request);
        if (!errors.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Invalid referral");
            body.put("fieldErrors", errors);
            return ResponseEntity.badRequest().body(body);
        }

        Referral referral = new Referral();
        referral.patientReference = request.patientReference.trim();
        referral.specialistOffice = request.specialistOffice.trim();
        // Creation ignores any client-supplied id and status; it is always NEW.
        referral.followUpDate = LocalDate.parse(request.followUpDate);
        referral.status = "NEW";
        repo.save(referral);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Created");
        body.put("referral", toResponse(referral, LocalDate.now()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/referrals/{id}/status")
    ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        if (!"NEW".equals(newStatus) && !"SENT".equals(newStatus) && !"DONE".equals(newStatus)) {
            Map<String, String> errors = new HashMap<>();
            errors.put("status", "must be NEW, SENT, or DONE");
            return badRequest("Invalid status", errors);
        }

        Referral referral = repo.findById(id)
                .orElseThrow(() -> new ReferralNotFoundException(id));
        referral.status = newStatus;
        repo.save(referral);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Updated");
        response.put("referral", toResponse(referral, LocalDate.now()));
        return ResponseEntity.ok(response);
    }

    private Map<String, String> validateCreate(ReferralRequest request) {
        Map<String, String> errors = new HashMap<>();
        if (request == null) {
            return errors; // body was empty; let the fields below flag
        }
        if (request.patientReference == null || request.patientReference.trim().length() < 3 || request.patientReference.trim().length() > 30) {
            errors.put("patientReference", "3 to 30 characters required");
        }
        if (request.specialistOffice == null || request.specialistOffice.trim().length() < 1 || request.specialistOffice.trim().length() > 100) {
            errors.put("specialistOffice", "1 to 100 characters required");
        }
        if (request.followUpDate == null) {
            errors.put("followUpDate", "A date is required");
        } else {
            try {
                LocalDate.parse(request.followUpDate);
            } catch (DateTimeParseException e) {
                errors.put("followUpDate", "Use YYYY-MM-DD");
            }
        }
        return errors;
    }

    private ReferralResponse toResponse(Referral referral, LocalDate today) {
        ReferralResponse response = new ReferralResponse();
        response.id = referral.id;
        response.patientReference = referral.patientReference;
        response.specialistOffice = referral.specialistOffice;
        response.followUpDate = referral.followUpDate != null ? referral.followUpDate.toString() : null;
        response.status = referral.status;
        response.overdue = Referral.isOverdue(referral.followUpDate, referral.status, today);
        return response;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message, Map<String, String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("fieldErrors", errors);
        return ResponseEntity.badRequest().body(body);
    }
}
