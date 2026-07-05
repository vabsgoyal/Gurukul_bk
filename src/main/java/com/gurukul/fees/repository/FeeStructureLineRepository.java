package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeStructureLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeStructureLineRepository extends JpaRepository<FeeStructureLine, UUID> {

	List<FeeStructureLine> findAllByFeeStructureId(UUID feeStructureId);

}
