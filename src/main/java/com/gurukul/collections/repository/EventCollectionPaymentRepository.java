package com.gurukul.collections.repository;

import com.gurukul.collections.entity.EventCollectionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface EventCollectionPaymentRepository extends JpaRepository<EventCollectionPayment, UUID> {

	List<EventCollectionPayment> findAllByEventId(UUID eventId);

	@Query("SELECT COALESCE(SUM(c.amount), 0) FROM EventCollectionPayment c WHERE c.event.id = :eventId")
	BigDecimal sumByEventId(@Param("eventId") UUID eventId);

}
