package com.gurukul.chat.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.ChatDtos.ContactResponse;
import com.gurukul.chat.dto.ChatDtos.ConversationResponse;
import com.gurukul.chat.dto.ChatDtos.CreateConversationRequest;
import com.gurukul.chat.dto.ChatDtos.MessagePageResponse;
import com.gurukul.chat.dto.ChatDtos.MessageResponse;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentRequest;
import com.gurukul.chat.dto.ChatDtos.PresignAttachmentResponse;
import com.gurukul.chat.dto.ChatDtos.UnreadCountResponse;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationParticipant;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.service.AttachmentService;
import com.gurukul.chat.service.ChatContactService;
import com.gurukul.chat.service.ConversationService;
import com.gurukul.chat.service.MessageService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat - Conversations", description = "1:1 (staff-staff / staff-student) conversations and the "
		+ "Helpdesk BOT conversation. Live sends happen over WebSocket/STOMP (/app/conversations/{id}/messages); "
		+ "these endpoints cover creation, listing, and history. Requires X-School-Id and Authorization headers.")
public class ConversationController {

	private final ConversationService conversationService;
	private final MessageService messageService;
	private final AttachmentService attachmentService;
	private final ChatContactService chatContactService;

	@PostMapping("/api/v1/chat/conversations")
	@Operation(summary = "Create (or fetch, if one already exists) a 1:1 conversation",
			description = "Staff-to-staff, staff-to-student, and parent-to-staff (a parent only with their child's "
					+ "teachers and the school's admins; a teacher only with parents of their students - see "
					+ "GET /api/v1/chat/contacts). Student-to-student is rejected.")
	public ApiResponse<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request) {
		AuthPrincipal principal = AuthContext.current();
		Conversation conversation = conversationService.createOneToOne(principal, request);
		return ApiResponse.success(toResponse(conversation));
	}

	@GetMapping("/api/v1/chat/conversations")
	@Operation(summary = "List my conversations (1:1 and BOT), newest activity first",
			description = "Each carries the participants' names, the newest message (lastMessage) and the caller's "
					+ "unreadCount - everything the chat list needs in one request, with no per-conversation calls.")
	public ApiResponse<List<ConversationResponse>> list() {
		AuthPrincipal principal = AuthContext.current();
		List<Conversation> conversations = conversationService.listForCaller(principal);
		List<UUID> ids = conversations.stream().map(Conversation::getId).toList();
		Map<UUID, List<ConversationParticipant>> participantsByConversation = conversationService.participantsOf(ids);
		Map<UUID, String> names = conversationService.namesOf(principal.getSchoolId(),
				participantsByConversation.values().stream().flatMap(List::stream).toList());
		Map<UUID, Message> latest = messageService.latestMessages(ids);
		Map<UUID, Long> unread = messageService.unreadCounts(principal, ids);

		List<ConversationResponse> responses = conversations.stream()
				.sorted(Comparator.comparing((Conversation c) -> lastActivity(c, latest)).reversed())
				.map(c -> ConversationResponse.from(c, participantsByConversation.getOrDefault(c.getId(), List.of()),
						names, latest.get(c.getId()), unread.getOrDefault(c.getId(), 0L)))
				.toList();
		return ApiResponse.success(responses);
	}

	@PostMapping("/api/v1/chat/conversations/{id}/read")
	@Operation(summary = "Mark a conversation read up to now",
			description = "Call when the chat is opened, and again when a message arrives while it's open. Clears "
					+ "its unreadCount on every device.")
	public ApiResponse<Void> markRead(@PathVariable UUID id) {
		AuthPrincipal principal = AuthContext.current();
		conversationService.requireParticipant(principal, id);
		messageService.markRead(principal, id);
		return ApiResponse.success(null, "Marked read");
	}

	@GetMapping("/api/v1/chat/unread-count")
	@Operation(summary = "Total unread chat messages", description = "For the Chats tab badge - one cheap query.")
	public ApiResponse<UnreadCountResponse> unreadCount() {
		return ApiResponse.success(new UnreadCountResponse(messageService.totalUnread(AuthContext.current())));
	}

	private static Instant lastActivity(Conversation conversation, Map<UUID, Message> latest) {
		Message message = latest.get(conversation.getId());
		return message != null ? message.getSentAt() : conversation.getUpdatedAt();
	}

	@GetMapping("/api/v1/chat/conversations/{id}/messages")
	@Operation(summary = "Paginated message history for a conversation, newest first")
	public ApiResponse<MessagePageResponse> history(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		AuthPrincipal principal = AuthContext.current();
		conversationService.requireParticipant(principal, id);
		Page<Message> result = messageService.history(id, PageRequest.of(page, size));
		return ApiResponse.success(new MessagePageResponse(
				result.getContent().stream()
						.map(m -> MessageResponse.from(m, attachmentService.presignDownload(m.getAttachmentObjectKey())))
						.toList(),
				result.hasNext()));
	}

	@PostMapping("/api/v1/chat/conversations/{id}/attachments/presign")
	@Operation(summary = "Request a presigned S3 upload slot for one image/PDF attachment",
			description = "Upload the raw file bytes to the returned uploadUrl directly (PUT, with the same "
					+ "Content-Type sent here), then send the message over STOMP with the returned objectKey.")
	public ApiResponse<PresignAttachmentResponse> presignAttachment(
			@PathVariable UUID id, @Valid @RequestBody PresignAttachmentRequest request) {
		AuthPrincipal principal = AuthContext.current();
		conversationService.requireParticipant(principal, id);
		return ApiResponse.success(attachmentService.presignUpload(principal, id, request));
	}

	@PostMapping("/api/v1/chat/bot/conversation")
	@Operation(summary = "Get (or create, on first use) my private Helpdesk BOT conversation")
	public ApiResponse<ConversationResponse> getOrCreateBotConversation() {
		AuthPrincipal principal = AuthContext.current();
		Conversation conversation = conversationService.getOrCreateBotConversation(principal);
		return ApiResponse.success(toResponse(conversation));
	}

	@GetMapping("/api/v1/chat/contacts")
	@Operation(summary = "Who I may start a parent-staff chat with, and why",
			description = "Parent: their children's class/subject teachers and the school's admins. Teacher: parents "
					+ "of students in sections they teach. Admin: every parent. Staff-to-staff and staff-to-student "
					+ "pairing is not listed here (unchanged: use the employee/student directories).")
	public ApiResponse<List<ContactResponse>> contacts() {
		return ApiResponse.success(chatContactService.contactsFor(AuthContext.current()));
	}

	private ConversationResponse toResponse(Conversation conversation) {
		List<ConversationParticipant> participants = conversationService.participantsOf(conversation.getId());
		return ConversationResponse.from(conversation, participants,
				conversationService.namesOf(conversation.getSchoolId(), participants));
	}

}
