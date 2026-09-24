package com.r3f3r.referrals;

// thrown when referral not found
public class ReferralNotFoundException extends RuntimeException {
    public ReferralNotFoundException(Long id) {
        super("Referral not found: " + id); // message
    }
}
