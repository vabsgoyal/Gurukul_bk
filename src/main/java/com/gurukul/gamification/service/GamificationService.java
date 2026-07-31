package com.gurukul.gamification.service;

import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.gamification.dto.GamificationDtos.GameProfileResponse;
import com.gurukul.gamification.entity.StudentGameProfile;
import com.gurukul.gamification.entity.XpEvent;
import com.gurukul.gamification.entity.XpSource;
import com.gurukul.gamification.repository.StudentGameProfileRepository;
import com.gurukul.gamification.repository.XpEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 of the gamification roadmap (specs/gamification/execution-plan.md): the XP ledger and
 * streak tracking every later phase (leagues, houses, badges) reads from.
 *
 * <p>Streak continuity is derived from attendance_record rows rather than a separate school
 * calendar: a row only exists for a date when a teacher actually marked that section's roster,
 * so walking a student's attendance history newest-first and stopping at the first ABSENT
 * naturally skips weekends/holidays (which never get a row at all) without needing to know which
 * calendar dates are school days.
 *
 * <p>Streak-freeze tokens and streak-milestone bonus XP are intentionally out of scope for this
 * first cut - noted as fast-follows in the execution plan.
 */
@Service
@RequiredArgsConstructor
public class GamificationService {

	private final StudentGameProfileRepository profileRepository;
	private final XpEventRepository xpEventRepository;
	private final AttendanceRecordRepository attendanceRecordRepository;

	@Transactional
	public void recordAttendanceXp(UUID schoolId, UUID studentId, LocalDate date, AttendanceStatus status) {
		int newAmount = xpForAttendance(status);
		StudentGameProfile profile = getOrCreateProfile(schoolId, studentId);

		xpEventRepository.findByStudentIdAndSourceAndRelatedDate(studentId, XpSource.ATTENDANCE, date)
				.ifPresentOrElse(
						existing -> applyAmountCorrection(profile, existing, newAmount),
						() -> {
							if (newAmount > 0) {
								XpEvent event = new XpEvent();
								event.setSchoolId(schoolId);
								event.setStudentId(studentId);
								event.setSource(XpSource.ATTENDANCE);
								event.setAmount(newAmount);
								event.setRelatedDate(date);
								xpEventRepository.save(event);
								profile.setTotalXp(profile.getTotalXp() + newAmount);
							}
						});

		recomputeStreak(schoolId, studentId, profile);
		profileRepository.save(profile);
	}

	public GameProfileResponse getMyProfile(AuthPrincipal principal) {
		if (principal.getOwnerType() != OwnerType.STUDENT) {
			throw new AccessDeniedException("Only a student account has a game profile");
		}
		StudentGameProfile profile = getOrCreateProfile(principal.getSchoolId(), principal.getOwnerId());
		return GameProfileResponse.from(profile, LevelCalculator.forTotalXp(profile.getTotalXp()));
	}

	/** Handles a teacher editing an already-marked day (e.g. ABSENT -> PRESENT) without double-counting. */
	private void applyAmountCorrection(StudentGameProfile profile, XpEvent existing, int newAmount) {
		int delta = newAmount - existing.getAmount();
		if (delta != 0) {
			existing.setAmount(newAmount);
			xpEventRepository.save(existing);
			profile.setTotalXp(profile.getTotalXp() + delta);
		}
	}

	private void recomputeStreak(UUID schoolId, UUID studentId, StudentGameProfile profile) {
		List<AttendanceRecord> history = attendanceRecordRepository
				.findAllBySchoolIdAndStudentIdOrderByAttendanceDateDesc(schoolId, studentId);
		int streak = 0;
		for (AttendanceRecord record : history) {
			if (record.getStatus() == AttendanceStatus.ABSENT) {
				break;
			}
			streak++;
		}
		profile.setCurrentStreakDays(streak);
		profile.setLongestStreakDays(Math.max(profile.getLongestStreakDays(), streak));
	}

	/** Also used by LeagueService, which needs the same get-or-create for peers on a leaderboard. */
	public StudentGameProfile getOrCreateProfile(UUID schoolId, UUID studentId) {
		return profileRepository.findBySchoolIdAndStudentId(schoolId, studentId)
				.orElseGet(() -> {
					StudentGameProfile profile = new StudentGameProfile();
					profile.setSchoolId(schoolId);
					profile.setStudentId(studentId);
					return profile;
				});
	}

	private int xpForAttendance(AttendanceStatus status) {
		return switch (status) {
			case PRESENT -> 10;
			case LATE -> 6;
			case HALF_DAY -> 5;
			case ABSENT -> 0;
		};
	}

}
