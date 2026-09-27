package com.gurukul.leads.repository;

import com.gurukul.leads.entity.DemoLead;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DemoLeadRepository extends JpaRepository<DemoLead, UUID> {

	long countByIpHashAndCreatedAtAfter(String ipHash, Instant after);

	List<DemoLead> findAllByOrderByCreatedAtDesc(Pageable pageable);

}
