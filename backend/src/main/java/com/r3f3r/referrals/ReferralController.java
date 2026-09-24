package com.r3f3r.referrals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired ReferralRepository repo;
    @Autowired HistoryRepo historyRepo;
    @Autowired ContactRepo contactRepo;
    @Autowired ProviderRepo providerRepo;

    @GetMapping("/referrals")
    List<ReferralResponse> all() {
        LocalDate today = LocalDate.now();
        List<Referral> data = repo.findAll(Sort.by(Sort.Direction.DESC, "id"));
        List<ReferralResponse> data2 = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            data2.add(toResponse(data.get(i), today));
        }
        return data2;
    }

    @PostMapping("/referrals")
    @Transactional
    public ResponseEntity<Map<String, Object>> create(@RequestBody ReferralRequest request) {
        Map<String, String> errors = validateCreate(request);
        if (!errors.isEmpty()) {
            return badRequest("Invalid referral", errors);
        }
        Referral referral = new Referral();
        referral.patientReference = request.patientReference.trim();
        if (request.providerId != null) {
            Provider p = providerRepo.findById(request.providerId).orElse(null);
            if (p == null) {
                throw new ProviderNotFoundException(request.providerId);
            }
            referral.providerId = p.id;
            referral.specialistOffice = p.name; // snapshot, renames dont touch it later
        } else {
            referral.specialistOffice = request.specialistOffice.trim();
        }
        // Creation ignores any client-supplied id and status; it is always NEW.
        referral.followUpDate = LocalDate.parse(request.followUpDate);
        referral.status = "NEW";
        repo.save(referral);
        return ResponseEntity.status(201).body(Utils.map("message", "Created", "referral", toResponse(referral, LocalDate.now())));
    }

    @PatchMapping("/referrals/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        // FIXME
        if (!"NEW".equals(newStatus) && !"SENT".equals(newStatus) && !"DONE".equals(newStatus)) {
            return badRequest("Invalid status", Utils.oneField("status", "must be " + Utils.joinStatuses(List.of("NEW", "SENT", "DONE"))));
        }
        Referral referral = repo.findLocked(id).orElseThrow(() -> new ReferralNotFoundException(id));
        String old = referral.status;
        if (!old.equals(newStatus)) {
            referral.status = newStatus;
            repo.save(referral);
            History h = new History();
            h.referralId = referral.id;
            h.fromStatus = old;
            h.toStatus = newStatus;
            h.changedAt = Instant.now();
            historyRepo.save(h);
            System.out.println("status changed " + id + " " + old + " -> " + newStatus);
        }
        return ResponseEntity.ok(Utils.map("message", "Updated", "referral", toResponse(referral, LocalDate.now())));
    }

    @GetMapping("/referrals/{id}/history")
    List<Map<String, Object>> history(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new ReferralNotFoundException(id);
        return historyRepo.forReferral(id).stream()
                .map(h -> Utils.map("id", h.id, "fromStatus", h.fromStatus, "toStatus", h.toStatus, "changedAt", h.changedAt.toString()))
                .collect(Collectors.toList());
    }

    @PatchMapping("/referrals/{id}/follow-up-date")
    @Transactional
    public ResponseEntity<Map<String, Object>> reschedule(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String d = body.get("followUpDate");
        if (d == null) return badRequest("Invalid date", Utils.oneField("followUpDate", "A date is required"));
        LocalDate newDate = Utils.parseDate(d);
        if (newDate == null) return badRequest("Invalid date", Utils.oneField("followUpDate", "Use YYYY-MM-DD"));

        Referral referral = repo.findLocked(id).orElseThrow(() -> new ReferralNotFoundException(id));
        if (!newDate.equals(referral.followUpDate)) {
            referral.followUpDate = newDate;
            repo.save(referral);
        }
        return ResponseEntity.ok(Utils.map("message", "Updated", "referral", toResponse(referral, LocalDate.now())));
    }

    @PostMapping("/referrals/{id}/contact-attempts")
    @Transactional
    public ResponseEntity<Map<String, Object>> addContact(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String channel = body.get("channel");
        String outcome = body.get("outcome");
        Map<String, String> errors = new java.util.HashMap<>();
        if (channel == null || !(channel.equals("PHONE") || channel.equals("EMAIL"))) {
            errors.put("channel", "must be PHONE or EMAIL");
        }
        if (outcome == null || !(outcome.equals("NO_RESPONSE") || outcome.equals("CALLBACK_REQUESTED") || outcome.equals("APPOINTMENT_CONFIRMED"))) {
            errors.put("outcome", "must be NO_RESPONSE, CALLBACK_REQUESTED, or APPOINTMENT_CONFIRMED");
        }
        if (!errors.isEmpty()) return badRequest("Invalid contact attempt", errors);
        repo.findLocked(id).orElseThrow(() -> new ReferralNotFoundException(id)); // lock same as the other writes

        Contact c = new Contact();
        c.referralId = id;
        c.channel = channel;
        c.outcome = outcome;
        c.recordedAt = Instant.now();
        contactRepo.save(c);
        return ResponseEntity.status(201).body(Utils.map("message", "Recorded", "attempt", contactMap(c)));
    }

    @GetMapping("/referrals/{id}/contact-attempts")
    List<Map<String, Object>> contacts(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new ReferralNotFoundException(id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Contact c : contactRepo.forReferral(id)) out.add(contactMap(c));
        return out;
    }

    private Map<String, Object> contactMap(Contact c) {
        return Utils.map("id", c.id, "channel", c.channel, "outcome", c.outcome, "recordedAt", c.recordedAt.toString());
    }

    // ---- providers (should probably be its own controller) ----

    @GetMapping("/providers")
    List<Map<String, Object>> providers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Provider p : providerRepo.allSorted()) out.add(Utils.map("id", p.id, "name", p.name));
        return out;
    }

    @PostMapping("/providers")
    ResponseEntity<Map<String, Object>> addProvider(@RequestBody Map<String, String> body) {
        return saveProvider(null, body.get("name"));
    }

    @PatchMapping("/providers/{id}")
    ResponseEntity<Map<String, Object>> renameProvider(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return saveProvider(id, body.get("name"));
    }

    // id == null means new provider
    private ResponseEntity<Map<String, Object>> saveProvider(Long id, String name) {
        String norm = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (Utils.isEmpty(name) || Utils.lenght(name) > Utils.MAX_LENGHT || norm.length() > 300) {
            return badRequest("Invalid provider", Utils.oneField("name", "1 to 100 characters required"));
        }
        Provider p = id == null ? new Provider() : providerRepo.findById(id).orElseThrow(() -> new ProviderNotFoundException(id));
        Provider other = providerRepo.findByNormalizedName(norm);
        if (other != null && !other.id.equals(p.id)) return duplicate();

        p.name = name.trim();
        p.normalizedName = norm;
        try {
            providerRepo.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // two people added the same one at the same time
            if (e.getMessage() != null && e.getMessage().toUpperCase().contains("PROVIDER_NORM_UQ")) return duplicate();
            throw e;
        }
        return ResponseEntity.status(id == null ? 201 : 200)
                .body(Utils.map("message", id == null ? "Created" : "Updated", "provider", Utils.map("id", p.id, "name", p.name)));
    }

    private ResponseEntity<Map<String, Object>> duplicate() {
        return ResponseEntity.status(409).body(Utils.map("message", "Duplicate provider", "fieldErrors", Utils.oneField("name", "A provider with this name already exists")));
    }

    // HACK validation, should use @Valid
    private Map<String, String> validateCreate(ReferralRequest request) {
        Map<String, String> errors = new java.util.HashMap<>();
        if (request != null) {
            if (request.patientReference != null) {
                if (Utils.lenght(request.patientReference) < 3 || Utils.lenght(request.patientReference) > 30) {
                    errors.put("patientReference", "3 to 30 characters required");
                }
            } else {
                errors.put("patientReference", "3 to 30 characters required");
            }
            if (request.providerId != null) {
                if (request.specialistOffice != null) {
                    errors.put("specialistOffice", "Choose a provider or enter an office, not both");
                }
            } else {
                if (Utils.isEmpty(request.specialistOffice) || Utils.lenght(request.specialistOffice) > Utils.MAX_LENGHT) {
                    errors.put("specialistOffice", "1 to 100 characters required");
                }
            }
            if (request.followUpDate != null) {
                if (Utils.parseDate(request.followUpDate) == null) {
                    errors.put("followUpDate", "Use YYYY-MM-DD");
                }
            } else {
                errors.put("followUpDate", "A date is required");
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
        response.providerId = referral.providerId;
        if (referral.followUpDate != null) {
            response.daysUntilFollowUp = ChronoUnit.DAYS.between(today, referral.followUpDate);
        }
        response.daysOverdue = response.overdue ? -response.daysUntilFollowUp : 0;
        return response;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message, Map<String, String> errors) {
        return ResponseEntity.badRequest().body(Utils.map("message", message, "fieldErrors", errors));
    }
}
