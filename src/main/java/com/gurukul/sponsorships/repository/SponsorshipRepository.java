package com.gurukul.sponsorships.repository;

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

}
