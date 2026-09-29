package com.gurukul.fees.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import com.gurukul.notifications.service.PushNotificationService.Notification;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import com.gurukul.parents.entity.ParentStudentLink;
import com.gurukul.parents.repository.ParentStudentLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily fee-due reminders to parents, driven by StudentFeeAssessment.dueDate: once in the three days
 * before the due date (window {@code PRE}), then once a week while it stays unpaid past it (overdue
 * day 1, 8, 15, ... - window {@code OVERDUE-<n>}).
 *
 * <p>Idempotent per assessment per window per parent: each alert goes through
 * PushNotificationService.sendOnce with the key {@code FEE_DUE:<assessmentId>:<window>}, so a rerun,
 * a restart or a second instance in the same window sends nothing new, and a day the job didn't run
 * is caught up by the next run while the window lasts. Payment in the app is on hold, so the push
 * opens the child's fee screen rather than a payment flow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeeDueAlertService {

	public static final String TYPE = "FEE_DUE";
	static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
	static final int REMIND_DAYS_BEFORE = 3;
	static final int OVERDUE_EVERY_DAYS = 7;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final StudentFeeAssessmentRepository assessmentRepository;
	private final ParentStudentLinkRepository parentStudentLinkRepository;
	private final PushNotificationService pushNotificationService;

	@Scheduled(cron = "${app.fees.due-alert-cron:0 0 9 * * *}", zone = "Asia/Kolkata")
	public void runDaily() {
		int sent = sendDueAlerts(LocalDate.now(SCHOOL_ZONE));
		if (sent > 0) {
			log.info("Fee-due alerts: notified {} parent(s)", sent);
		}
	}

	/** Returns how many parent notifications were newly sent. Never throws for one bad assessment. */
	public int sendDueAlerts(LocalDate today) {
		List<StudentFeeAssessment> open = assessmentRepository.findAllByDueDateLessThanEqualAndStatusNot(
				today.plusDays(REMIND_DAYS_BEFORE), FeeAssessmentStatus.PAID);
		int sent = 0;
		Map<UUID, List<StudentFeeAssessment>> bySchool = open.stream()
				.collect(Collectors.groupingBy(StudentFeeAssessment::getSchoolId));
		for (Map.Entry<UUID, List<StudentFeeAssessment>> school : bySchool.entrySet()) {
			Map<UUID, List<UUID>> parentsByStudent = parentStudentLinkRepository
					.findAllBySchoolIdAndStudentIdIn(school.getKey(),
							school.getValue().stream().map(a -> a.getStudent().getId()).collect(Collectors.toSet()))
					.stream()
					.collect(Collectors.groupingBy(ParentStudentLink::getStudentId,
							Collectors.mapping(ParentStudentLink::getParentId, Collectors.toList())));
			for (StudentFeeAssessment assessment : school.getValue()) {
				try {
					sent += alert(assessment, today, parentsByStudent.getOrDefault(assessment.getStudent().getId(), List.of()));
				} catch (Exception e) {
					log.warn("Fee-due alert for assessment {} failed", assessment.getId(), e);
				}
			}
		}
		return sent;
	}

	private int alert(StudentFeeAssessment assessment, LocalDate today, List<UUID> parentIds) {
		BigDecimal balance = assessment.getTotalDue().subtract(
				assessment.getTotalPaid() != null ? assessment.getTotalPaid() : BigDecimal.ZERO);
		Optional<String> window = window(assessment.getDueDate(), today);
		if (parentIds.isEmpty() || balance.signum() <= 0 || window.isEmpty()) {
			return 0;
		}
		String name = assessment.getStudent().getName();
		String amount = "₹" + balance.stripTrailingZeros().toPlainString();
		String due = DATE_FORMAT.format(assessment.getDueDate());
		boolean overdue = today.isAfter(assessment.getDueDate());
		String body = overdue
				? name + "'s fee of " + amount + " was due on " + due + " and is overdue."
				: name + "'s fee of " + amount + " is due on " + due + ".";
		List<Recipient> recipients = parentIds.stream().distinct().map(id -> new Recipient(OwnerType.PARENT, id)).toList();
		return pushNotificationService.sendOnce(assessment.getSchoolId(), PushChannel.ALERTS,
				new Notification(recipients, (overdue ? "Fee overdue: " : "Fee due soon: ") + name, body,
						Map.of("type", TYPE,
								"studentId", String.valueOf(assessment.getStudent().getId()),
								"assessmentId", String.valueOf(assessment.getId()))),
				"FEE_DUE:" + assessment.getId() + ":" + window.get());
	}

	/**
	 * {@code PRE} from three days before the due date through the due date itself; {@code OVERDUE-n}
	 * for overdue days 7n+1 .. 7n+7; empty earlier than that (or with no due date).
	 */
	static Optional<String> window(LocalDate dueDate, LocalDate today) {
		if (dueDate == null) {
			return Optional.empty();
		}
		long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
		if (daysUntilDue > REMIND_DAYS_BEFORE) {
			return Optional.empty();
		}
		if (daysUntilDue >= 0) {
			return Optional.of("PRE");
		}
		long daysOverdue = -daysUntilDue;
		return Optional.of("OVERDUE-" + (daysOverdue - 1) / OVERDUE_EVERY_DAYS);
	}

}
