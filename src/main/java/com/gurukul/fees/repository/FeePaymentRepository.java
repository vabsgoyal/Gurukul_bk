package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {

	Optional<FeePayment> findByIdAndSchoolId(UUID id, UUID schoolId);

}
