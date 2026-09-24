package com.r3f3r.referrals;

// response
public class ReferralResponse {
    public Long id; // id
    public String patientReference; // patient reference
    public String specialistOffice; // office
    public String followUpDate; // date
    public String status; // status
    public boolean overdue; // overdue
    public long daysUntilFollowUp; // days until
    public long daysOverdue; // days overdue
    public Long providerId; // provider id
    // public String department;
}
