package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleRoom;
import com.gurukul.gamification.entity.BattleRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BattleRoomRepository extends JpaRepository<BattleRoom, UUID> {

	Optional<BattleRoom> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<BattleRoom> findBySchoolIdAndRoomCode(UUID schoolId, String roomCode);

	List<BattleRoom> findAllByStatus(BattleRoomStatus status);

	/** Auto-match target: oldest still-open room for this class+subject, if any. */
	List<BattleRoom> findAllBySchoolIdAndClassNameAndAcademicYearAndSubjectIdAndStatusOrderByCreatedAtAsc(
			UUID schoolId, String className, String academicYear, UUID subjectId, BattleRoomStatus status);

}
