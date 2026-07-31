package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.XpEvent;
import com.gurukul.gamification.entity.XpSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

	Optional<XpEvent> findByStudentIdAndSourceAndRelatedDate(UUID studentId, XpSource source, LocalDate relatedDate);

}
