package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeUpiQrLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeeUpiQrLogRepository extends JpaRepository<FeeUpiQrLog, UUID> {
}
