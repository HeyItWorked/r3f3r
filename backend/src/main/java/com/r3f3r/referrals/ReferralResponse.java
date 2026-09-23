package com.r3f3r.referrals;

// What the frontend reads. Dates are formatted YYYY-MM-DD as plain strings.
public class ReferralResponse {
    public Long id;
    public String patientReference;
    public String specialistOffice;
    public String followUpDate;
    public String status;
    public boolean overdue;
}
