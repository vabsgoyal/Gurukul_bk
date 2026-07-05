package com.gurukul.sponsorships.repository;

import com.gurukul.sponsorships.entity.SponsorshipPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface SponsorshipPaymentRepository extends JpaRepository<SponsorshipPayment, UUID> {

	@Query("SELECT COALESCE(SUM(p.amount), 0) FROM SponsorshipPayment p WHERE p.sponsorship.id = :sponsorshipId")
	BigDecimal sumBySponsorshipId(@Param("sponsorshipId") UUID sponsorshipId);

}
