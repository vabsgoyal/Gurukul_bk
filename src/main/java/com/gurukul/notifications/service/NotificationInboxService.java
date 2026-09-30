package com.gurukul.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.EntityNotFoundException;
import com.gurukul.notifications.entity.Notification;
import com.gurukul.notifications.repository.NotificationRepository;
import com.gurukul.notifications.service.PushNotificationService.Recipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The per-user notification inbox. Writes run in their own transaction (REQUIRES_NEW): an inbox row
 * is a side effect of whatever triggered the push, so a failure here must never mark the caller's
 * transaction rollback-only (posting an announcement must not fail because its inbox rows didn't
 * save), and a caller's rollback can't take the row with it after the push already went out.
 *
 * <p>Reads take an explicit AuthPrincipal and only ever return the caller's own rows - the owner is
 * always the token's, never something in the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationInboxService {

	static final int TITLE_MAX = 255;

	private final NotificationRepository notificationRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** Saves one row per distinct recipient. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(UUID schoolId, Collection<Recipient> recipients, String title, String body,
			Map<String, Object> data) {
		List<Notification> rows = new ArrayList<>();
		for (Recipient recipient : new LinkedHashSet<>(recipients)) {
			rows.add(row(schoolId, recipient, title, body, data, null));
		}
		notificationRepository.saveAll(rows);
	}

	/**
	 * Saves a row with {@code dedupeKey} for every recipient who doesn't already have one, and returns
	 * exactly those recipients - the ones who should now be pushed. The unique index on
	 * (recipient, dedupe_key) is the backstop when two callers race: the loser's insert fails, this
	 * whole claim rolls back, and the caller gets an exception (and sends nothing - the winner does).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<Recipient> claim(UUID schoolId, Collection<Recipient> recipients, String title, String body,
			Map<String, Object> data, String dedupeKey) {
		List<Recipient> fresh = unclaimed(recipients, dedupeKey);
		notificationRepository.saveAllAndFlush(
				fresh.stream().map(r -> row(schoolId, r, title, body, data, dedupeKey)).toList());
		return fresh;
	}

	/** Recipients with no inbox row for {@code dedupeKey} yet - one read, no writes. */
	@Transactional(readOnly = true)
	public List<Recipient> unclaimed(Collection<Recipient> recipients, String dedupeKey) {
		Map<OwnerType, Set<UUID>> idsByType = new EnumMap<>(OwnerType.class);
		for (Recipient recipient : recipients) {
			idsByType.computeIfAbsent(recipient.ownerType(), t -> new HashSet<>()).add(recipient.ownerId());
		}
		Set<Recipient> alreadySent = new HashSet<>();
		idsByType.forEach((ownerType, ids) -> notificationRepository
				.findRecipientIdsWithDedupeKey(ownerType, ids, dedupeKey)
				.forEach(id -> alreadySent.add(new Recipient(ownerType, id))));
		return new LinkedHashSet<>(recipients).stream()
				.filter(r -> !alreadySent.contains(r))
				.toList();
	}

	@Transactional(readOnly = true)
	public Slice<Notification> listMine(AuthPrincipal principal, Pageable pageable) {
		return notificationRepository.findAllBySchoolIdAndRecipientOwnerTypeAndRecipientOwnerIdOrderByCreatedAtDesc(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId(), pageable);
	}

	@Transactional(readOnly = true)
	public long unreadCount(AuthPrincipal principal) {
		return notificationRepository.countBySchoolIdAndRecipientOwnerTypeAndRecipientOwnerIdAndReadAtIsNull(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId());
	}

	/** 404 (not 403) for someone else's row, so ids can't be probed for existence. */
	@Transactional
	public Notification markRead(AuthPrincipal principal, UUID notificationId) {
		Notification notification = notificationRepository
				.findByIdAndSchoolIdAndRecipientOwnerTypeAndRecipientOwnerId(
						notificationId, principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId())
				.orElseThrow(() -> new EntityNotFoundException("Notification not found"));
		if (notification.getReadAt() == null) {
			notification.setReadAt(Instant.now());
		}
		return notificationRepository.save(notification);
	}

	@Transactional
	public int markAllRead(AuthPrincipal principal) {
		return notificationRepository.markAllRead(
				principal.getSchoolId(), principal.getOwnerType(), principal.getOwnerId(), Instant.now());
	}

	public Map<String, Object> parseData(Notification notification) {
		if (notification.getData() == null || notification.getData().isBlank()) {
			return Map.of();
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> parsed = objectMapper.readValue(notification.getData(), Map.class);
			return parsed;
		} catch (JsonProcessingException e) {
			log.warn("Unreadable data on notification {}", notification.getId());
			return Map.of();
		}
	}

	private Notification row(UUID schoolId, Recipient recipient, String title, String body,
			Map<String, Object> data, String dedupeKey) {
		Notification notification = new Notification();
		notification.setSchoolId(schoolId);
		notification.setRecipientOwnerType(recipient.ownerType());
		notification.setRecipientOwnerId(recipient.ownerId());
		Object type = data != null ? data.get("type") : null;
		notification.setType(type != null ? String.valueOf(type) : "GENERAL");
		String safeTitle = title == null || title.isBlank() ? "Smart Gurukul" : title;
		notification.setTitle(safeTitle.length() > TITLE_MAX ? safeTitle.substring(0, TITLE_MAX) : safeTitle);
		notification.setBody(body == null ? "" : body);
		notification.setData(toJson(data));
		notification.setDedupeKey(dedupeKey);
		return notification;
	}

	private String toJson(Map<String, Object> data) {
		if (data == null || data.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			return null;
		}
	}

}
