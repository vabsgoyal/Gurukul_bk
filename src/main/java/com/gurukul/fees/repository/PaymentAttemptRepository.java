package com.gurukul.fees.repository;

import com.gurukul.fees.entity.PaymentAttempt;
import com.gurukul.fees.entity.PaymentAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

	Optional<PaymentAttempt> findByTransactionRefAndSchoolId(String transactionRef, UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdOrderByCreatedAtDesc(UUID assessmentId, UUID schoolId);

	List<PaymentAttempt> findAllByAssessmentIdAndSchoolIdAndStatusIn(
			UUID assessmentId, UUID schoolId, List<PaymentAttemptStatus> statuses);

	/**
	 * Deliberately not scoped by schoolId: the Razorpay webhook carries no X-School-Id header, so
	 * the order id is the only handle available - the school is then read off the attempt itself.
	 * Safe because razorpay_order_id is globally unique (see V62).
	 *
	 * <p>PESSIMISTIC_WRITE because the checkout callback and the webhook race to confirm the same
	 * attempt, and both would otherwise read status=INITIATED and each record a FeePayment. The lock
	 * makes the loser wait and observe the winner's VERIFIED status.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PaymentAttempt> findByRazorpayOrderId(String razorpayOrderId);

}
