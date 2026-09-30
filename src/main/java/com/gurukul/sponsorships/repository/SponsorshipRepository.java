package com.gurukul.sponsorships.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.gurukul.sponsorships.entity.Sponsorship;
import com.gurukul.sponsorships.entity.SponsorshipPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SponsorshipRepository extends JpaRepository<Sponsorship, UUID> {

	List<Sponsorship> findAllBySchoolId(UUID schoolId);

	List<Sponsorship> findAllBySchoolIdAndPurpose(UUID schoolId, SponsorshipPurpose purpose);

	Optional<Sponsorship> findByIdAndSchoolId(UUID id, UUID schoolId);

	/** Row-locked while a payment is recorded, so two payments can't both pass the remaining-pledge check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Sponsorship s where s.id = :id and s.schoolId = :schoolId")
	Optional<Sponsorship> findForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);

}
