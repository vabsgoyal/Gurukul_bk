package com.gurukul.expenses.infrastructure.repository;

import com.gurukul.expenses.infrastructure.entity.InfraVendorPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InfraVendorPaymentRepository extends JpaRepository<InfraVendorPayment, UUID> {

	Optional<InfraVendorPayment> findByPurchaseId(UUID purchaseId);

}
