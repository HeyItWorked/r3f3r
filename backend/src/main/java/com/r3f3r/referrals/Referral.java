package com.r3f3r.referrals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "referral")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "referral_seq")
    @SequenceGenerator(name = "referral_seq", sequenceName = "referral_seq", allocationSize = 1)
    public Long id;

    @Column(name = "patient_reference", length = 30, nullable = false)
    public String patientReference;

    @Column(name = "specialist_office", length = 100, nullable = false)
    public String specialistOffice;

    @Column(name = "follow_up_date", nullable = false)
    public LocalDate followUpDate;

    @Column(length = 20, nullable = false)
    public String status = "NEW";

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    // A referral is overdue when the follow-up date is before the backend's
    // current date and it is not marked DONE. Today is not overdue.
    public static boolean isOverdue(LocalDate followUpDate, String status, LocalDate today) {
        if (followUpDate == null || status == null) {
            return false;
        }
        if ("DONE".equals(status)) {
            return false;
        }
        return followUpDate.isBefore(today);
    }
}
