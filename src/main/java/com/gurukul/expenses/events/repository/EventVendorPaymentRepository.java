package com.gurukul.expenses.events.repository;

import com.gurukul.expenses.events.entity.EventVendorPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface EventVendorPaymentRepository extends JpaRepository<EventVendorPayment, UUID> {

	@Query("""
			SELECT COALESCE(SUM(p.request.estimatedAmount), 0) FROM EventVendorPayment p
			WHERE p.request.budgetLine.budget.event.id = :eventId
			""")
	BigDecimal sumPaidExpensesByEventId(@Param("eventId") UUID eventId);

}
