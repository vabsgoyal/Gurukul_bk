package com.gurukul.admissions.repository;

import com.gurukul.admissions.entity.AdmissionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionDocumentRepository extends JpaRepository<AdmissionDocument, UUID> {

	List<AdmissionDocument> findAllByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

	Optional<AdmissionDocument> findByIdAndApplicationIdAndSchoolId(UUID id, UUID applicationId, UUID schoolId);

	boolean existsByObjectKey(String objectKey);

	void deleteAllByApplicationId(UUID applicationId);

}
