package com.gurukul.finance.repository;

import com.gurukul.finance.entity.ReceiptSequence;
import com.gurukul.finance.entity.ReceiptSequenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT rs FROM ReceiptSequence rs
			WHERE rs.schoolId = :schoolId AND rs.sequenceType = :type AND rs.academicYear = :academicYear
			""")
	Optional<ReceiptSequence> findForUpdate(
			@Param("schoolId") UUID schoolId,
			@Param("type") ReceiptSequenceType type,
			@Param("academicYear") String academicYear);

}
