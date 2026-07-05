package com.gurukul.expenses.infrastructure.repository;

import com.gurukul.expenses.infrastructure.entity.InfraPurchaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InfraPurchaseRecordRepository extends JpaRepository<InfraPurchaseRecord, UUID> {

	Optional<InfraPurchaseRecord> findByRequestId(UUID requestId);

}
