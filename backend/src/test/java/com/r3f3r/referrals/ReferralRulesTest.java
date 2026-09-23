package com.r3f3r.referrals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReferralRulesTest {

    private final LocalDate today = LocalDate.of(2026, 9, 23);

    @Test
    void todayIsNotOverdue() {
        assertFalse(Referral.isOverdue(today, "NEW", today));
        assertFalse(Referral.isOverdue(today, "SENT", today));
    }

    @Test
    void aPastOutstandingReferralIsOverdue() {
        LocalDate yesterday = today.minusDays(1);
        assertTrue(Referral.isOverdue(yesterday, "NEW", today));
        assertTrue(Referral.isOverdue(yesterday, "SENT", today));
    }

    @Test
    void aDoneReferralIsNeverOverdue() {
        LocalDate yesterday = today.minusDays(1);
        assertFalse(Referral.isOverdue(yesterday, "DONE", today));
    }

    @Test
    void aFutureReferralIsNeverOverdue() {
        LocalDate nextWeek = today.plusWeeks(1);
        assertFalse(Referral.isOverdue(nextWeek, "NEW", today));
    }

    @Test
    void aNullDateOrStatusIsNotOverdue() {
        assertFalse(Referral.isOverdue(null, "NEW", today));
        assertFalse(Referral.isOverdue(today, null, today));
    }

    @Test
    void returningAPastDueDoneReferralToSentMakesItOverdueAgain() {
        LocalDate yesterday = today.minusDays(1);
        assertFalse(Referral.isOverdue(yesterday, "DONE", today));
        assertTrue(Referral.isOverdue(yesterday, "SENT", today));
    }
}
