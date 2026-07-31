package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.XpEvent;
import com.gurukul.gamification.entity.XpSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

	Optional<XpEvent> findByStudentIdAndSourceAndRelatedDate(UUID studentId, XpSource source, LocalDate relatedDate);

	@Query("SELECT COALESCE(SUM(e.amount), 0) FROM XpEvent e WHERE e.studentId = :studentId AND e.createdAt >= :since")
	long sumAmountSince(@Param("studentId") UUID studentId, @Param("since") Instant since);

}
