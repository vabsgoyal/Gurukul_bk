package com.gurukul.chat.websocket;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.service.AnnouncementService;
import com.gurukul.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorizes STOMP SUBSCRIBE frames against the three chat topic shapes. Reuses
 * ConversationService.requireParticipant and AnnouncementService's visibility checks so live WS
 * delivery and REST history/listing never disagree.
 */
@Component
@RequiredArgsConstructor
public class StompSubscribeAuthorizationInterceptor implements ChannelInterceptor {

	private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");
	private static final Pattern SCHOOL_ANNOUNCEMENTS_TOPIC =
			Pattern.compile("^/topic/schools/([0-9a-fA-F-]{36})/announcements$");
	private static final Pattern SECTION_ANNOUNCEMENTS_TOPIC =
			Pattern.compile("^/topic/sections/([0-9a-fA-F-]{36})/announcements$");
	private static final Pattern USER_CALLS_TOPIC =
			Pattern.compile("^/topic/users/([0-9a-fA-F-]{36})/(EMPLOYEE|STUDENT)/([0-9a-fA-F-]{36})/calls$");

	private final ConversationService conversationService;
	private final AnnouncementService announcementService;

	@Override
	public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
			return message;
		}

		String destination = accessor.getDestination();
		if (destination == null) {
			throw new StompAuthenticationException("Missing destination on SUBSCRIBE");
		}
		AuthPrincipal principal = requirePrincipal(accessor);

		Matcher conversationMatch = CONVERSATION_TOPIC.matcher(destination);
		if (conversationMatch.matches()) {
			conversationService.requireParticipant(principal, UUID.fromString(conversationMatch.group(1)));
			return message;
		}

		Matcher schoolMatch = SCHOOL_ANNOUNCEMENTS_TOPIC.matcher(destination);
		if (schoolMatch.matches()) {
			if (!announcementService.isSchoolVisibleTo(principal, UUID.fromString(schoolMatch.group(1)))) {
				throw new StompAuthenticationException("Not authorized for this school's announcements");
			}
			return message;
		}

		Matcher sectionMatch = SECTION_ANNOUNCEMENTS_TOPIC.matcher(destination);
		if (sectionMatch.matches()) {
			if (!announcementService.isSectionVisibleTo(principal, UUID.fromString(sectionMatch.group(1)))) {
				throw new StompAuthenticationException("Not authorized for this section's announcements");
			}
			return message;
		}

		Matcher userCallsMatch = USER_CALLS_TOPIC.matcher(destination);
		if (userCallsMatch.matches()) {
			boolean isOwnTopic = principal.getSchoolId().toString().equalsIgnoreCase(userCallsMatch.group(1))
					&& principal.getOwnerType() == OwnerType.valueOf(userCallsMatch.group(2))
					&& principal.getOwnerId().toString().equalsIgnoreCase(userCallsMatch.group(3));
			if (!isOwnTopic) {
				throw new StompAuthenticationException("You may only subscribe to your own call topic");
			}
			return message;
		}

		throw new StompAuthenticationException("Unknown or disallowed destination: " + destination);
	}

	private AuthPrincipal requirePrincipal(StompHeaderAccessor accessor) {
		if (!(accessor.getUser() instanceof StompPrincipal stompPrincipal)) {
			throw new StompAuthenticationException("Not authenticated");
		}
		return stompPrincipal.getAuthPrincipal();
	}

}
