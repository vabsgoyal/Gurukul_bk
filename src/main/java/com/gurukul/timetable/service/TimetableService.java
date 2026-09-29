package com.gurukul.timetable.service;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import com.gurukul.academics.repository.SectionSubjectTeacherRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.parents.service.ParentService;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.timetable.dto.TimetableDtos.ClashResponse;
import com.gurukul.timetable.dto.TimetableDtos.PeriodRequest;
import com.gurukul.timetable.dto.TimetableDtos.PeriodResponse;
import com.gurukul.timetable.dto.TimetableDtos.PeriodScheduleRequest;
import com.gurukul.timetable.dto.TimetableDtos.PeriodScheduleResponse;
import com.gurukul.timetable.dto.TimetableDtos.SectionTimetableRequest;
import com.gurukul.timetable.dto.TimetableDtos.SlotRequest;
import com.gurukul.timetable.dto.TimetableDtos.SlotResponse;
import com.gurukul.timetable.dto.TimetableDtos.TimetableResponse;
import com.gurukul.timetable.entity.PeriodDefinition;
import com.gurukul.timetable.entity.TimetableSetting;
import com.gurukul.timetable.entity.TimetableSlot;
import com.gurukul.timetable.repository.PeriodDefinitionRepository;
import com.gurukul.timetable.repository.TimetableSettingRepository;
import com.gurukul.timetable.repository.TimetableSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bell schedule + weekly timetables. Only an admin writes (also gated in SecurityConfig); reads are
 * scoped here: a teacher sees sections they class-teach or teach in, a student their own section,
 * a parent a linked child's section - same pattern as ReportCardService/AttendanceService.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

	static final List<DayOfWeek> WEEKDAYS = List.of(
			DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

	private static final Comparator<SlotResponse> SLOT_ORDER = Comparator
			.comparing(SlotResponse::getDayOfWeek)
			.thenComparingInt(SlotResponse::getPeriodNumber);

	private final PeriodDefinitionRepository periodRepository;
	private final TimetableSettingRepository settingRepository;
	private final TimetableSlotRepository slotRepository;
	private final SectionSubjectTeacherRepository sectionSubjectTeacherRepository;
	private final ClassSectionService classSectionService;
	private final StudentRepository studentRepository;
	private final ParentService parentService;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final SchoolContext schoolContext;

	// ---------------------------------------------------------------- bell schedule

	@Transactional(readOnly = true)
	public PeriodScheduleResponse getSchedule() {
		UUID schoolId = schoolContext.getSchoolId();
		boolean saturday = isSaturdayEnabled(schoolId);
		return new PeriodScheduleResponse(saturday, schoolDays(saturday), loadPeriods(schoolId));
	}

	@Transactional
	public PeriodScheduleResponse replaceSchedule(PeriodScheduleRequest request) {
		requireAdmin();
		UUID schoolId = schoolContext.getSchoolId();
		List<PeriodRequest> periods = request.getPeriods().stream()
				.sorted(Comparator.comparing(PeriodRequest::getPeriodNumber))
				.toList();
		validatePeriods(periods);

		// Refuse changes that would leave saved slots pointing at a period/day that no longer exists.
		Set<Integer> teachable = periods.stream()
				.filter(p -> !p.isBreakPeriod())
				.map(PeriodRequest::getPeriodNumber)
				.collect(Collectors.toSet());
		Set<Integer> orphaned = new TreeSet<>(slotRepository.findDistinctPeriodNumbersInUse(schoolId));
		orphaned.removeAll(teachable);
		if (!orphaned.isEmpty()) {
			throw new TimetableConflictException("Period(s) " + joinNumbers(orphaned)
					+ " are still used in class timetables. Clear them from those timetables before removing"
					+ " them or turning them into a break.");
		}
		if (!request.isSaturdayEnabled()
				&& slotRepository.countBySchoolIdAndDayOfWeek(schoolId, DayOfWeek.SATURDAY) > 0) {
			throw new TimetableConflictException(
					"Some class timetables still have Saturday periods. Clear them before switching Saturday off.");
		}

		periodRepository.deleteAllBySchoolId(schoolId);
		List<PeriodDefinition> saved = new ArrayList<>();
		for (PeriodRequest p : periods) {
			PeriodDefinition def = new PeriodDefinition();
			def.setSchoolId(schoolId);
			def.setPeriodNumber(p.getPeriodNumber());
			def.setStartTime(p.getStartTime());
			def.setEndTime(p.getEndTime());
			def.setBreakPeriod(p.isBreakPeriod());
			def.setLabel(p.getLabel() == null || p.getLabel().isBlank() ? null : p.getLabel().trim());
			saved.add(def);
		}
		periodRepository.saveAll(saved);

		TimetableSetting setting = settingRepository.findBySchoolId(schoolId).orElseGet(() -> {
			TimetableSetting s = new TimetableSetting();
			s.setSchoolId(schoolId);
			return s;
		});
		setting.setSaturdayEnabled(request.isSaturdayEnabled());
		settingRepository.save(setting);

		return new PeriodScheduleResponse(request.isSaturdayEnabled(), schoolDays(request.isSaturdayEnabled()),
				saved.stream().map(TimetableService::toPeriodResponse).toList());
	}

	private static void validatePeriods(List<PeriodRequest> sortedPeriods) {
		Set<Integer> numbers = new HashSet<>();
		PeriodRequest previous = null;
		for (PeriodRequest p : sortedPeriods) {
			if (!numbers.add(p.getPeriodNumber())) {
				throw new IllegalArgumentException("Period " + p.getPeriodNumber() + " is listed more than once");
			}
			if (!p.getStartTime().isBefore(p.getEndTime())) {
				throw new IllegalArgumentException("Period " + p.getPeriodNumber() + " must end after it starts");
			}
			if (previous != null && p.getStartTime().isBefore(previous.getEndTime())) {
				throw new IllegalArgumentException("Period " + p.getPeriodNumber() + " overlaps period "
						+ previous.getPeriodNumber() + " (periods must be in time order and not overlap)");
			}
			previous = p;
		}
	}

	// ---------------------------------------------------------------- section timetable

	@Transactional(readOnly = true)
	public TimetableResponse getSectionTimetable(UUID sectionId) {
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		requireCanViewSection(AuthContext.current(), section);
		return buildSectionView(section);
	}

	/**
	 * Replaces the section's whole week. Every check runs before any row is touched, so a rejected
	 * save leaves the previous timetable intact.
	 */
	@Transactional
	public TimetableResponse replaceSectionTimetable(UUID sectionId, SectionTimetableRequest request) {
		requireAdmin();
		UUID schoolId = schoolContext.getSchoolId();
		ClassSection section = classSectionService.getScopedClassSection(sectionId);
		boolean saturday = isSaturdayEnabled(schoolId);
		Set<DayOfWeek> allowedDays = new HashSet<>(schoolDays(saturday));
		Map<Integer, PeriodDefinition> periodsByNumber = periodRepository
				.findAllBySchoolIdOrderByPeriodNumberAsc(schoolId).stream()
				.collect(Collectors.toMap(PeriodDefinition::getPeriodNumber, p -> p));

		// subjectId|teacherId -> this section's assignment row: the only pairs a slot may use.
		Map<String, SectionSubjectTeacher> assignments = new HashMap<>();
		for (SectionSubjectTeacher a : sectionSubjectTeacherRepository.findAllBySectionId(sectionId)) {
			assignments.put(pairKey(a.getSubject().getId(), a.getTeacher().getId()), a);
		}

		Set<String> seenDayPeriod = new HashSet<>();
		List<TimetableSlot> newSlots = new ArrayList<>();
		for (SlotRequest r : request.getSlots()) {
			String where = dayLabel(r.getDayOfWeek()) + " period " + r.getPeriodNumber();
			if (!allowedDays.contains(r.getDayOfWeek())) {
				throw new IllegalArgumentException(where + ": " + dayLabel(r.getDayOfWeek()) + " is not a school day"
						+ (r.getDayOfWeek() == DayOfWeek.SATURDAY ? " (Saturday is switched off in the bell schedule)" : ""));
			}
			PeriodDefinition period = periodsByNumber.get(r.getPeriodNumber());
			if (period == null) {
				throw new IllegalArgumentException(where + ": this period is not in the bell schedule");
			}
			if (period.isBreakPeriod()) {
				throw new IllegalArgumentException(where + ": this period is a break");
			}
			if (!seenDayPeriod.add(r.getDayOfWeek() + "|" + r.getPeriodNumber())) {
				throw new IllegalArgumentException(where + " is listed more than once");
			}
			SectionSubjectTeacher assignment = assignments.get(pairKey(r.getSubjectId(), r.getTeacherId()));
			if (assignment == null) {
				throw new IllegalArgumentException(where
						+ ": that subject and teacher are not assigned to this class. Assign them under subjects first.");
			}
			TimetableSlot slot = new TimetableSlot();
			slot.setSchoolId(schoolId);
			slot.setSection(section);
			slot.setAcademicYear(section.getAcademicYear());
			slot.setDayOfWeek(r.getDayOfWeek());
			slot.setPeriodNumber(r.getPeriodNumber());
			slot.setSubject(assignment.getSubject());
			slot.setTeacher(assignment.getTeacher());
			newSlots.add(slot);
		}

		List<ClashResponse> clashes = findClashes(schoolId, section, newSlots);
		if (!clashes.isEmpty()) {
			throw new TimetableClashException(clashes.size() == 1
					? "A teacher is already teaching another class at that time"
					: clashes.size() + " periods clash with other classes' timetables", clashes);
		}

		slotRepository.deleteAllBySchoolIdAndSectionId(schoolId, sectionId);
		try {
			slotRepository.saveAllAndFlush(newSlots);
		} catch (DataIntegrityViolationException e) {
			// Another admin saved a clashing slot between our check and our insert; the DB unique
			// constraint caught it and this transaction rolls back.
			throw new TimetableClashException("The timetable changed while saving. Reload and try again.", List.of());
		}
		return buildSectionView(section);
	}

	/**
	 * A teacher clashes if another section of the same academic year already has them at that
	 * day+period. A teacher can't clash with themselves inside the payload: day+period is already
	 * unique per section (checked above), and a payload only ever covers one section.
	 */
	private List<ClashResponse> findClashes(UUID schoolId, ClassSection section, List<TimetableSlot> newSlots) {
		if (newSlots.isEmpty()) {
			return List.of();
		}
		Set<UUID> teacherIds = newSlots.stream().map(s -> s.getTeacher().getId()).collect(Collectors.toSet());
		Map<String, TimetableSlot> busy = new HashMap<>();
		for (TimetableSlot other : slotRepository.findClashCandidates(
				schoolId, section.getAcademicYear(), teacherIds, section.getId())) {
			busy.put(clashKey(other.getTeacher().getId(), other.getDayOfWeek(), other.getPeriodNumber()), other);
		}
		List<ClashResponse> clashes = new ArrayList<>();
		for (TimetableSlot slot : newSlots) {
			TimetableSlot other = busy.get(clashKey(slot.getTeacher().getId(), slot.getDayOfWeek(), slot.getPeriodNumber()));
			if (other != null) {
				clashes.add(new ClashResponse(slot.getDayOfWeek(), slot.getPeriodNumber(),
						slot.getTeacher().getId(), slot.getTeacher().getName(),
						other.getSection().getId(),
						other.getSection().getClassName() + " - " + other.getSection().getSection()));
			}
		}
		return clashes;
	}

	// ---------------------------------------------------------------- my timetable

	@Transactional(readOnly = true)
	public TimetableResponse getMyTimetable(UUID childId) {
		AuthPrincipal principal = AuthContext.current();
		UUID schoolId = schoolContext.getSchoolId();
		return switch (principal.getRole()) {
			case ADMIN, TEACHER -> {
				if (principal.getOwnerType() != OwnerType.EMPLOYEE) {
					throw new AccessDeniedException("This account has no staff timetable");
				}
				yield buildTeacherView(schoolId, principal.getOwnerId());
			}
			case STUDENT -> {
				Student student = studentRepository.findByIdAndSchoolId(principal.getOwnerId(), schoolId)
						.orElseThrow(() -> new EntityNotFoundException("Student not found"));
				yield buildSectionView(student.getClassSection());
			}
			case PARENT -> buildSectionView(resolveParentChild(principal, childId).getClassSection());
		};
	}

	private Student resolveParentChild(AuthPrincipal principal, UUID childId) {
		if (childId != null) {
			return parentService.requireLinkedChild(principal.getOwnerId(), childId, principal.getSchoolId());
		}
		List<ParentStudentLink> links = parentStudentLinkRepository.findAllByParentId(principal.getOwnerId());
		if (links.size() != 1) {
			throw new IllegalArgumentException(links.isEmpty()
					? "No child is linked to this account"
					: "Choose which child's timetable to show (childId)");
		}
		return parentService.requireLinkedChild(principal.getOwnerId(), links.get(0).getStudentId(), principal.getSchoolId());
	}

	// ---------------------------------------------------------------- access

	private static void requireAdmin() {
		if (AuthContext.current().getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can change the timetable");
		}
	}

	private void requireCanViewSection(AuthPrincipal principal, ClassSection section) {
		boolean allowed = switch (principal.getRole()) {
			case ADMIN -> true;
			case TEACHER -> principal.getOwnerType() == OwnerType.EMPLOYEE
					&& ((section.getClassTeacher() != null && section.getClassTeacher().getId().equals(principal.getOwnerId()))
					|| sectionSubjectTeacherRepository.existsBySectionIdAndTeacherId(section.getId(), principal.getOwnerId()));
			case STUDENT -> principal.getOwnerType() == OwnerType.STUDENT
					&& studentRepository.findByIdAndSchoolId(principal.getOwnerId(), principal.getSchoolId())
							.map(s -> s.getClassSection().getId().equals(section.getId()))
							.orElse(false);
			case PARENT -> parentStudentLinkRepository.findAllByParentId(principal.getOwnerId()).stream()
					.map(link -> studentRepository.findByIdAndSchoolId(link.getStudentId(), principal.getSchoolId()))
					.anyMatch(s -> s.isPresent() && s.get().getClassSection().getId().equals(section.getId()));
		};
		if (!allowed) {
			throw new AccessDeniedException("You can't view this class's timetable");
		}
	}

	// ---------------------------------------------------------------- views

	private TimetableResponse buildSectionView(ClassSection section) {
		UUID schoolId = schoolContext.getSchoolId();
		boolean saturday = isSaturdayEnabled(schoolId);
		List<SlotResponse> slots = slotRepository.findAllBySchoolIdAndSectionId(schoolId, section.getId()).stream()
				.map(TimetableService::toSlotResponse)
				.sorted(SLOT_ORDER)
				.toList();
		return new TimetableResponse("SECTION", section.getId(), section.getDisplayLabel(), null, null,
				section.getAcademicYear(), saturday, schoolDays(saturday), loadPeriods(schoolId), slots);
	}

	/** A teacher's own week: the latest academic year they have any slot in (empty if none yet). */
	private TimetableResponse buildTeacherView(UUID schoolId, UUID teacherId) {
		boolean saturday = isSaturdayEnabled(schoolId);
		String year = slotRepository.findLatestAcademicYearForTeacher(schoolId, teacherId);
		List<TimetableSlot> slots = year == null
				? List.of()
				: slotRepository.findAllBySchoolIdAndTeacherIdAndAcademicYear(schoolId, teacherId, year);
		String teacherName = slots.isEmpty() ? null : slots.get(0).getTeacher().getName();
		return new TimetableResponse("TEACHER", null, null, teacherId, teacherName, year, saturday,
				schoolDays(saturday), loadPeriods(schoolId),
				slots.stream().map(TimetableService::toSlotResponse).sorted(SLOT_ORDER).toList());
	}

	private boolean isSaturdayEnabled(UUID schoolId) {
		return settingRepository.findBySchoolId(schoolId).map(TimetableSetting::isSaturdayEnabled).orElse(false);
	}

	private List<PeriodResponse> loadPeriods(UUID schoolId) {
		return periodRepository.findAllBySchoolIdOrderByPeriodNumberAsc(schoolId).stream()
				.map(TimetableService::toPeriodResponse)
				.toList();
	}

	static List<DayOfWeek> schoolDays(boolean saturdayEnabled) {
		if (!saturdayEnabled) {
			return WEEKDAYS;
		}
		List<DayOfWeek> days = new ArrayList<>(WEEKDAYS);
		days.add(DayOfWeek.SATURDAY);
		return List.copyOf(days);
	}

	private static PeriodResponse toPeriodResponse(PeriodDefinition p) {
		return new PeriodResponse(p.getPeriodNumber(), p.getStartTime(), p.getEndTime(), p.isBreakPeriod(), p.getLabel());
	}

	private static SlotResponse toSlotResponse(TimetableSlot s) {
		return new SlotResponse(s.getDayOfWeek(), s.getPeriodNumber(),
				s.getSubject().getId(), s.getSubject().getName(), s.getSubject().getCode(),
				s.getTeacher().getId(), s.getTeacher().getName(),
				s.getSection().getId(), s.getSection().getClassName(), s.getSection().getSection());
	}

	private static String pairKey(UUID subjectId, UUID teacherId) {
		return subjectId + "|" + teacherId;
	}

	private static String clashKey(UUID teacherId, DayOfWeek day, int period) {
		return teacherId + "|" + day + "|" + period;
	}

	private static String dayLabel(DayOfWeek day) {
		String name = day.name();
		return name.charAt(0) + name.substring(1).toLowerCase();
	}

	private static String joinNumbers(Set<Integer> numbers) {
		return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}

}
