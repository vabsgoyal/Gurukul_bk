package com.gurukul.expenses.events.repository;

import com.gurukul.expenses.events.entity.EventBudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventBudgetLineRepository extends JpaRepository<EventBudgetLine, UUID> {

	List<EventBudgetLine> findAllByBudgetId(UUID budgetId);

}
