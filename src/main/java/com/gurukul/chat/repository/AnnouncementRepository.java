package com.gurukul.chat.repository;

import com.gurukul.chat.entity.Announcement;
import com.gurukul.chat.entity.AnnouncementScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

	Optional<Announcement> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<Announcement> findAllBySchoolIdAndScopeOrderByCreatedAtDesc(UUID schoolId, AnnouncementScope scope);

	List<Announcement> findAllBySectionIdOrderByCreatedAtDesc(UUID sectionId);

	List<Announcement> findAllBySchoolIdAndClassNameOrderByCreatedAtDesc(UUID schoolId, String className);

}
