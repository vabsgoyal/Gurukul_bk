package com.gurukul.vendors.repository;

import com.gurukul.vendors.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

	List<Vendor> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Optional<Vendor> findByIdAndSchoolId(UUID id, UUID schoolId);

}
