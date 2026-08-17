package com.gurukul.exams.repository;

import com.gurukul.exams.entity.ReportCardPublication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportCardPublicationRepository extends JpaRepository<ReportCardPublication, UUID> {

	boolean existsByClassSection_IdAndTerm(UUID classSectionId, String term);

	@EntityGraph(attributePaths = {"publishedByEmployee"})
	Optional<ReportCardPublication> findByClassSection_IdAndTerm(UUID classSectionId, String term);

	List<ReportCardPublication> findAllByClassSection_IdOrderByPublishedAtDesc(UUID classSectionId);

}
