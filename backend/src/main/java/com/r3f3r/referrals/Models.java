package com.r3f3r.referrals;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

// all the new tables live here, easier than 3 files

@Entity
@Table(name = "referral_status_history")
class History {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "history_seq")
    @SequenceGenerator(name = "history_seq", sequenceName = "history_seq", allocationSize = 1)
    public Long id;
    @Column(name = "referral_id", nullable = false) public Long referralId;
    @Column(name = "from_status", length = 20, nullable = false) public String fromStatus;
    @Column(name = "to_status", length = 20, nullable = false) public String toStatus;
    @Column(name = "changed_at", nullable = false) public Instant changedAt;
}

@Entity
@Table(name = "referral_contact_attempt")
class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "contact_seq")
    @SequenceGenerator(name = "contact_seq", sequenceName = "contact_seq", allocationSize = 1)
    public Long id;
    @Column(name = "referral_id", nullable = false) public Long referralId;
    @Column(length = 10, nullable = false) public String channel;
    @Column(length = 30, nullable = false) public String outcome;
    @Column(name = "recorded_at", nullable = false) public Instant recordedAt;
}

@Entity
@Table(name = "provider")
class Provider {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "provider_seq")
    @SequenceGenerator(name = "provider_seq", sequenceName = "provider_seq", allocationSize = 1)
    public Long id;
    @Column(length = 100, nullable = false) public String name;
    @Column(name = "normalized_name", length = 300, nullable = false) public String normalizedName;
}
