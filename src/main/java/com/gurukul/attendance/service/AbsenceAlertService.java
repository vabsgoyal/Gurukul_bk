package com.gurukul.attendance.service;

import com.gurukul.attendance.entity.AttendanceStatus;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import com.gurukul.notifications.service.PushNotificationService.Notification;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import com.gurukul.students.entity.Student;
import com.gurukul.students.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells a child's parents, as soon as the register is saved, that the child was marked ABSENT today.
 *
 * <p>At most once per child per day: the push goes through PushNotificationService.sendOnce with the
 * key {@code ABSENCE:<studentId>:<date>}, so re-saving the register, or correcting ABSENT -> PRESENT
 * -> ABSENT, never alerts twice; a correction to PRESENT sends nothing (only ABSENT publishes the
 * event). "Today" is the school's date (Asia/Kolkata), not the server's.
 *
 * <p>Runs after the attendance transaction commits. By then that transaction is finished, so any
 * work here needs its own (REQUIRES_NEW) - a plain @Transactional would join the already-committed
 * one and its writes would silently never commit.
 */
@Service
@Slf4j
public class AbsenceAlertService {

	public static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
	public static final String TYPE = "ABSENCE_ALERT";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final StudentRepository studentRepository;
	private final AttendanceRecordRepository attendanceRecordRepository;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final PushNotificationService pushNotificationService;
	/** Self-reference through the proxy, so {@link #buildAlert}'s own transaction applies. */
	private final AbsenceAlertService self;

	public AbsenceAlertService(
			StudentRepository studentRepository,
			AttendanceRecordRepository attendanceRecordRepository,
			ParentStudentLinkRepository parentStudentLinkRepository,
			PushNotificationService pushNotificationService,
			@Lazy AbsenceAlertService self) {
		this.studentRepository = studentRepository;
		this.attendanceRecordRepository = attendanceRecordRepository;
		this.parentStudentLinkRepository = parentStudentLinkRepository;
		this.pushNotificationService = pushNotificationService;
		this.self = self;
	}

	public static String dedupeKey(UUID studentId, LocalDate date) {
		return "ABSENCE:" + studentId + ":" + date;
	}

	/** Never throws: an alert is a courtesy, the register is already saved. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAbsenceMarked(AbsenceMarkedEvent event) {
		try {
			self.buildAlert(event).ifPresent(notification -> pushNotificationService.sendOnce(
					event.schoolId(), PushChannel.ALERTS, notification, dedupeKey(event.studentId(), event.date())));
		} catch (Exception e) {
			log.warn("Absence alert for student {} on {} failed", event.studentId(), event.date(), e);
		}
	}

	/**
	 * Empty when there's nobody to tell, or the record is no longer ABSENT (a later save in the same
	 * moment already corrected it).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Optional<Notification> buildAlert(AbsenceMarkedEvent event) {
		boolean stillAbsent = attendanceRecordRepository
				.findBySchoolIdAndStudentIdAndAttendanceDate(event.schoolId(), event.studentId(), event.date())
				.filter(r -> r.getStatus() == AttendanceStatus.ABSENT)
				.isPresent();
		if (!stillAbsent) {
			return Optional.empty();
		}
		Optional<Student> student = studentRepository.findByIdAndSchoolId(event.studentId(), event.schoolId());
		if (student.isEmpty()) {
			return Optional.empty();
		}
		List<Recipient> parents = parentStudentLinkRepository
				.findAllBySchoolIdAndStudentIdIn(event.schoolId(), List.of(event.studentId())).stream()
				.map(link -> new Recipient(OwnerType.PARENT, link.getParentId()))
				.distinct()
				.toList();
		if (parents.isEmpty()) {
			return Optional.empty();
		}
		String name = student.get().getName();
		return Optional.of(new Notification(parents,
				"Absence alert: " + name,
				name + " was marked absent today (" + DATE_FORMAT.format(event.date()) + ").",
				Map.of("type", TYPE, "studentId", String.valueOf(event.studentId()), "date", event.date().toString())));
	}

}
