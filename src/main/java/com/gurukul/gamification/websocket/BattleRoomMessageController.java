package com.gurukul.gamification.websocket;

import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.websocket.StompPrincipal;
import com.gurukul.gamification.dto.BattleRoomDtos.SubmitBattleAnswerRequest;
import com.gurukul.gamification.service.BattleRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * Buzz and answer are the only latency-sensitive Battle Room actions, so they go over STOMP
 * (not REST) for the lowest possible round-trip - everything else (create/join/match/lobby state)
 * is plain REST via BattleRoomController. Result is broadcast to /topic/battle-rooms/{roomId} by
 * BattleRoomService itself, not returned here, same as ChatMessageController's pattern.
 */
@Controller
@RequiredArgsConstructor
public class BattleRoomMessageController {

	private final BattleRoomService battleRoomService;

	@MessageMapping("/battle-rooms/{roomId}/buzz")
	public void buzz(@DestinationVariable UUID roomId, SimpMessageHeaderAccessor accessor) {
		battleRoomService.buzz(requirePrincipal(accessor), roomId);
	}

	@MessageMapping("/battle-rooms/{roomId}/answer")
	public void answer(
			@DestinationVariable UUID roomId,
			@Payload SubmitBattleAnswerRequest request,
			SimpMessageHeaderAccessor accessor) {
		battleRoomService.submitAnswer(requirePrincipal(accessor), roomId, request);
	}

	private AuthPrincipal requirePrincipal(SimpMessageHeaderAccessor accessor) {
		if (!(accessor.getUser() instanceof StompPrincipal stompPrincipal)) {
			throw new AccessDeniedException("Not authenticated");
		}
		return stompPrincipal.getAuthPrincipal();
	}

}
