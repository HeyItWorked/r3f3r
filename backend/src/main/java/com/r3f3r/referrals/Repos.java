package com.r3f3r.referrals;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface HistoryRepo extends JpaRepository<History, Long> {
    @Query("select h from History h where h.referralId = ?1 order by h.changedAt desc, h.id desc")
    List<History> forReferral(Long referralId);
}

interface ContactRepo extends JpaRepository<Contact, Long> {
    @Query("select c from Contact c where c.referralId = ?1 order by c.recordedAt desc, c.id desc")
    List<Contact> forReferral(Long referralId);
}

interface ProviderRepo extends JpaRepository<Provider, Long> {
    @Query("select p from Provider p order by p.normalizedName asc, p.id asc")
    List<Provider> allSorted();

    Provider findByNormalizedName(String normalizedName);
}
