package com.gurukul.gamification.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.gamification.dto.GamificationDtos.LeaderboardEntryResponse;
import com.gurukul.gamification.dto.GamificationDtos.LeaderboardResponse;
import com.gurukul.gamification.entity.LeagueTier;
import com.gurukul.gamification.entity.StudentGameProfile;
import com.gurukul.gamification.repository.StudentGameProfileRepository;
import com.gurukul.gamification.repository.XpEventRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * League leaderboards (Phase 2 of specs/gamification/execution-plan.md). Deliberately leaner
 * than that doc's original League/LeagueMembership sketch: weekly XP is never stored, only
 * computed live from XpEvent for the current week, so the only persisted state is which tier a
 * student is currently in (StudentGameProfile.currentTier) - nothing to keep in sync when XP is
 * awarded. A "league" is simply every student in the caller's own class-section who's currently
 * in the same tier - no separate League/LeagueMembership rows needed.
 */
@Service
@RequiredArgsConstructor
public class LeagueService {

	private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
	/** Below this many peers in a tier group, promotion/relegation is skipped - not enough people to judge fairly. */
	private static final int MIN_GROUP_SIZE_FOR_MOVEMENT = 3;
	private static final double MOVEMENT_FRACTION = 0.2;

	private final StudentRepository studentRepository;
	private final StudentGameProfileRepository profileRepository;
	private final XpEventRepository xpEventRepository;
	private final GamificationService gamificationService;

	public LeaderboardResponse getLeaderboard(AuthPrincipal principal) {
		if (principal.getOwnerType() != OwnerType.STUDENT) {
			throw new AccessDeniedException("Only a student account has a leaderboard");
		}
		UUID schoolId = principal.getSchoolId();
		UUID studentId = principal.getOwnerId();

		Student self = studentRepository.findByIdAndSchoolId(studentId, schoolId)
				.orElseThrow(() -> new EntityNotFoundException("Student not found"));
		StudentGameProfile selfProfile = gamificationService.getOrCreateProfile(schoolId, studentId);
		LeagueTier tier = selfProfile.getCurrentTier();

		List<Student> sectionStudents = studentRepository
				.findAllBySchoolIdAndClassSectionId(schoolId, self.getClassSection().getId());
		Map<UUID, StudentGameProfile> profilesById = profileRepository
				.findAllBySchoolIdAndStudentIdIn(schoolId, sectionStudents.stream().map(Student::getId).toList())
				.stream()
				.collect(Collectors.toMap(StudentGameProfile::getStudentId, Function.identity()));

		Instant weekStart = currentWeekStart();
		record Scored(Student student, long weeklyXp) {
		}
		List<Scored> scored = sectionStudents.stream()
				.filter(s -> tierOf(profilesById.get(s.getId())) == tier)
				.map(s -> new Scored(s, xpEventRepository.sumAmountSince(s.getId(), weekStart)))
				.sorted(Comparator.comparingLong(Scored::weeklyXp).reversed())
				.toList();

		List<LeaderboardEntryResponse> entries = new ArrayList<>();
		int yourRank = 0;
		for (int i = 0; i < scored.size(); i++) {
			Scored sc = scored.get(i);
			boolean isYou = sc.student().getId().equals(studentId);
			if (isYou) {
				yourRank = i + 1;
			}
			entries.add(new LeaderboardEntryResponse(i + 1, sc.student().getId(), sc.student().getName(), sc.weeklyXp(), isYou));
		}

		return new LeaderboardResponse(
				tier, self.getClassSection().getDisplayLabel(), entries, yourRank,
				selfProfile.getCurrentStreakDays(), selfProfile.getLongestStreakDays());
	}

	/**
	 * Runs just after each week rolls over. At that moment "now" is still effectively the instant
	 * the new week started, so summing XP since the start of the week that just ended captures
	 * that whole completed week (the sliver of the new week that's elapsed by then is negligible).
	 */
	@Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Kolkata")
	@Transactional
	public void runWeeklyPromotionSweep() {
		Instant completedWeekStart = currentWeekStart().minusSeconds(7L * 24 * 3600);

		Map<UUID, List<StudentGameProfile>> bySchool = profileRepository.findAll().stream()
				.collect(Collectors.groupingBy(StudentGameProfile::getSchoolId));

		bySchool.forEach((schoolId, profiles) -> {
			Map<UUID, UUID> classSectionByStudentId = studentRepository
					.findAllById(profiles.stream().map(StudentGameProfile::getStudentId).toList())
					.stream()
					.collect(Collectors.toMap(Student::getId, s -> s.getClassSection().getId()));

			record Group(UUID classSectionId, LeagueTier tier) {
			}
			Map<Group, List<StudentGameProfile>> groups = profiles.stream()
					.filter(p -> classSectionByStudentId.containsKey(p.getStudentId()))
					.collect(Collectors.groupingBy(
							p -> new Group(classSectionByStudentId.get(p.getStudentId()), p.getCurrentTier())));

			groups.forEach((group, groupProfiles) -> {
				Map<UUID, Long> weeklyXpByStudentId = groupProfiles.stream()
						.collect(Collectors.toMap(
								StudentGameProfile::getStudentId,
								p -> xpEventRepository.sumAmountSince(p.getStudentId(), completedWeekStart)));
				List<StudentGameProfile> ranked = groupProfiles.stream()
						.sorted(Comparator.comparingLong((StudentGameProfile p) -> weeklyXpByStudentId.get(p.getStudentId())).reversed())
						.toList();
				if (ranked.size() < MIN_GROUP_SIZE_FOR_MOVEMENT) {
					return;
				}
				int moveCount = Math.max(1, (int) (ranked.size() * MOVEMENT_FRACTION));
				for (int i = 0; i < moveCount; i++) {
					promote(ranked.get(i));
				}
				for (int i = ranked.size() - moveCount; i < ranked.size(); i++) {
					relegate(ranked.get(i));
				}
				profileRepository.saveAll(ranked);
			});
		});
	}

	private void promote(StudentGameProfile profile) {
		LeagueTier[] tiers = LeagueTier.values();
		int next = profile.getCurrentTier().ordinal() + 1;
		if (next < tiers.length) {
			profile.setCurrentTier(tiers[next]);
		}
	}

	private void relegate(StudentGameProfile profile) {
		int previous = profile.getCurrentTier().ordinal() - 1;
		if (previous >= 0) {
			profile.setCurrentTier(LeagueTier.values()[previous]);
		}
	}

	private LeagueTier tierOf(StudentGameProfile profile) {
		return profile != null ? profile.getCurrentTier() : LeagueTier.BRONZE;
	}

	private Instant currentWeekStart() {
		return LocalDate.now(SCHOOL_ZONE)
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.atStartOfDay(SCHOOL_ZONE)
				.toInstant();
	}

}
