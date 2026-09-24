package com.r3f3r.referrals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// tests
class ReferralRulesTest {

    @Test
    void test1() {
        LocalDate today = LocalDate.of(2026, 9, 23);
        LocalDate yesterday = today.minusDays(1); // yesterday
        assertFalse(Referral.isOverdue(today, "NEW", today));
        assertFalse(Referral.isOverdue(today, "SENT", today));
        assertTrue(Referral.isOverdue(yesterday, "NEW", today));
        assertTrue(Referral.isOverdue(yesterday, "SENT", today));
        assertFalse(Referral.isOverdue(yesterday, "DONE", today));
        assertFalse(Referral.isOverdue(today.plusWeeks(1), "NEW", today));
        assertFalse(Referral.isOverdue(null, "NEW", today));
        assertFalse(Referral.isOverdue(today, null, today));
    }
}
