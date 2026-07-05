package com.gurukul.sponsorships.repository;

import com.gurukul.sponsorships.entity.Sponsor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SponsorRepository extends JpaRepository<Sponsor, UUID> {

	List<Sponsor> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Optional<Sponsor> findByIdAndSchoolId(UUID id, UUID schoolId);

}
