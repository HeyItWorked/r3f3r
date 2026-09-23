package com.r3f3r.referrals;

public class ReferralNotFoundException extends RuntimeException {
    public ReferralNotFoundException(Long id) {
        super("Referral not found: " + id);
    }
}
