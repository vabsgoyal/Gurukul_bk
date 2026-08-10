package com.gurukul.fees.repository;

import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

	Optional<PaymentAttempt> findByTransactionRefAndSchoolId(String transactionRef, UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdOrderByCreatedAtDesc(UUID assessmentId, UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdAndStatusIn(
			UUID assessmentId, UUID schoolId, List<PaymentAttemptStatus> statuses);

}
