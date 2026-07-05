package com.gurukul.finance.repository;

import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.TransactionDirection;
import com.gurukul.finance.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

	List<FinancialTransaction> findAllBySchoolIdOrderByTransactionDateDescCreatedAtDesc(UUID schoolId);

	Optional<FinancialTransaction> findByIdAndSchoolId(UUID id, UUID schoolId);

	@Query("""
			SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t
			WHERE t.schoolId = :schoolId AND t.direction = :direction AND t.status = :status
			""")
	BigDecimal sumBySchoolIdAndDirectionAndStatus(
			@Param("schoolId") UUID schoolId,
			@Param("direction") TransactionDirection direction,
			@Param("status") TransactionStatus status);

}
