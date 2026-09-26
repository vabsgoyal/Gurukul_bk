package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.BattleRoom;
import com.gurukul.gamification.entity.BattleRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BattleRoomRepository extends JpaRepository<BattleRoom, UUID> {

	Optional<BattleRoom> findByIdAndSchoolId(UUID id, UUID schoolId);

	/**
	 * Row-locks the room for the rest of the transaction. Answers and the sweep both take it, so
	 * "was that the last answer?" / "has this question timed out?" and the advance that follows
	 * can't interleave - otherwise two last answers landing together could each miss the other and
	 * leave the question open until it times out.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM BattleRoom r WHERE r.id = :id")
	Optional<BattleRoom> findByIdForUpdate(@Param("id") UUID id);

	Optional<BattleRoom> findBySchoolIdAndRoomCode(UUID schoolId, String roomCode);

	List<BattleRoom> findAllByStatus(BattleRoomStatus status);

	@Query("SELECT r.id FROM BattleRoom r WHERE r.status = :status")
	List<UUID> findIdsByStatus(@Param("status") BattleRoomStatus status);

	/** Auto-match target: oldest still-open room for this class+subject, if any. */
	List<BattleRoom> findAllBySchoolIdAndClassNameAndAcademicYearAndSubjectIdAndStatusOrderByCreatedAtAsc(
			UUID schoolId, String className, String academicYear, UUID subjectId, BattleRoomStatus status);

	/** Browse list: every WAITING/ACTIVE room for the caller's own class, optionally filtered to one subject. */
	@Query("SELECT r FROM BattleRoom r WHERE r.schoolId = :schoolId AND r.className = :className "
			+ "AND r.academicYear = :academicYear AND r.status IN ('WAITING', 'ACTIVE') "
			+ "AND (:subjectId IS NULL OR r.subject.id = :subjectId) "
			+ "ORDER BY r.createdAt DESC")
	List<BattleRoom> findBrowsableRoomsForClass(
			@Param("schoolId") UUID schoolId,
			@Param("className") String className,
			@Param("academicYear") String academicYear,
			@Param("subjectId") UUID subjectId);

}
