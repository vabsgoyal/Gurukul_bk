package com.gurukul.expenses.events.repository;

import com.gurukul.expenses.events.entity.EventExpenseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventExpenseRequestRepository extends JpaRepository<EventExpenseRequest, UUID> {

	List<EventExpenseRequest> findAllByBudgetLineBudgetEventId(UUID eventId);

	Optional<EventExpenseRequest> findByIdAndSchoolId(UUID id, UUID schoolId);

}
