package com.gurukul.expenses.events.repository;

import com.gurukul.expenses.events.entity.EventBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventBudgetRepository extends JpaRepository<EventBudget, UUID> {

	Optional<EventBudget> findByEventId(UUID eventId);

}
