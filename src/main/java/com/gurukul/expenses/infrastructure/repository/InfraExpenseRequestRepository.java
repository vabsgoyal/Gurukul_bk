package com.gurukul.expenses.infrastructure.repository;

import com.gurukul.expenses.infrastructure.entity.InfraExpenseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InfraExpenseRequestRepository extends JpaRepository<InfraExpenseRequest, UUID> {

	List<InfraExpenseRequest> findAllBySchoolId(UUID schoolId);

	Optional<InfraExpenseRequest> findByIdAndSchoolId(UUID id, UUID schoolId);

}
