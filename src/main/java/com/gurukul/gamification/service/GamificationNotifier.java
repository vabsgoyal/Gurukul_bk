package com.gurukul.gamification.service;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.gamification.event.BattleRoomOpenedEvent;
import com.gurukul.gamification.event.QuizChallengeCreatedEvent;
import com.gurukul.notifications.service.PushChannel;
import com.gurukul.notifications.service.PushNotificationService;
import com.gurukul.notifications.service.PushNotificationService.Notification;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import com.gurukul.students.entity.StudentStatus;
import com.gurukul.students.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Push notifications for quiz challenges and battle rooms. Runs after the triggering transaction
 * commits (so nobody is told about a room that rolled back) and off the request thread (so a
 * class-wide fan-out never slows down creating the room). Never throws - a notification is a courtesy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GamificationNotifier {

	private final PushNotificationService pushNotificationService;
	private final StudentRepository studentRepository;

	/**
	 * A class can open many rooms in a row; each student hears about at most one per this window,
	 * so battles don't turn into notification spam. Rooms opened in between still show in the
	 * app's browse list.
	 */
	@Value("${app.gamification.battle-room.notify-cooldown-minutes:10}")
	private long battleRoomNotifyCooldownMinutes;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onQuizChallengeCreated(QuizChallengeCreatedEvent event) {
		try {
			pushNotificationService.sendToOwner(event.schoolId(), OwnerType.STUDENT, event.opponentStudentId(),
					PushChannel.GAMES,
					event.challengerName() + " challenged you!",
					"Beat them at " + event.subjectName() + " - you have 48 hours to answer.",
					Map.of("type", "QUIZ_CHALLENGE", "challengeId", event.challengeId().toString()));
		} catch (Exception e) {
			log.warn("Challenge notification for {} failed", event.challengeId(), e);
		}
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBattleRoomOpened(BattleRoomOpenedEvent event) {
		try {
			if (event.joinWindowEndsAt().isBefore(Instant.now())) {
				return;
			}
			List<Recipient> recipients = studentRepository
					.findIdsInClass(event.schoolId(), event.className(), event.academicYear(), StudentStatus.ACTIVE)
					.stream()
					.filter(id -> !id.equals(event.creatorStudentId()))
					.map(id -> new Recipient(OwnerType.STUDENT, id))
					.toList();
			if (recipients.isEmpty()) {
				return;
			}
			Notification notification = new Notification(recipients,
					event.creatorName() + " opened a " + event.subjectName() + " battle",
					"Tap to join before it starts - room code " + event.roomCode(),
					Map.of("type", "BATTLE_ROOM_OPEN",
							"roomId", event.roomId().toString(),
							"roomCode", event.roomCode(),
							"joinWindowEndsAt", event.joinWindowEndsAt().toString()));
			pushNotificationService.sendOnce(event.schoolId(), PushChannel.GAMES, notification, cooldownKey(event));
		} catch (Exception e) {
			log.warn("Battle room notification for {} failed", event.roomId(), e);
		}
	}

	/** Same key for every room a class opens within one cooldown window, so each student gets one push per window. */
	private String cooldownKey(BattleRoomOpenedEvent event) {
		long window = event.joinWindowEndsAt().getEpochSecond() / (battleRoomNotifyCooldownMinutes * 60);
		return "BATTLE_ROOM_OPEN:" + event.className() + ":" + event.academicYear() + ":" + window;
	}

}
