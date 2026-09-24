package com.r3f3r.referrals;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    // row lock so two status changes cant both read the same "from" status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.id = ?1")
    Optional<Referral> findLocked(Long id);
}
