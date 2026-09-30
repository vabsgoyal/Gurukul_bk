package com.gurukul.fees.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

	Optional<PaymentAttempt> findByTransactionRefAndSchoolId(String transactionRef, UUID schoolId);

	/** Row-locked, so two concurrent results for one attempt are handled one after the other. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from PaymentAttempt a where a.transactionRef = :transactionRef and a.schoolId = :schoolId")
	Optional<PaymentAttempt> findForUpdate(@Param("transactionRef") String transactionRef, @Param("schoolId") UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdOrderByCreatedAtDesc(UUID assessmentId, UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdAndStatusIn(
			UUID assessmentId, UUID schoolId, List<PaymentAttemptStatus> statuses);

}
