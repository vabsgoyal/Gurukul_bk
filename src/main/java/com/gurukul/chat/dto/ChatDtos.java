package com.gurukul.chat.dto;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.chat.entity.Conversation;
import com.gurukul.chat.entity.ConversationParticipant;
import com.gurukul.chat.entity.ConversationType;
import com.gurukul.chat.entity.Message;
import com.gurukul.chat.entity.SenderKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatDtos {

	@Getter @Setter
	@Schema(description = "Create (or fetch, if one already exists) a 1:1 conversation with another party")
	public static class CreateConversationRequest {
		@NotNull private OwnerType otherPartyOwnerType;
		@NotNull private UUID otherPartyOwnerId;
	}

	@Getter @AllArgsConstructor
	public static class ParticipantResponse {
		private OwnerType ownerType;
		private UUID ownerId;
		@Schema(description = "Display name, resolved server-side (null if that person no longer exists)")
		private String name;

		public static ParticipantResponse from(ConversationParticipant participant, Map<UUID, String> namesById) {
			return new ParticipantResponse(participant.getOwnerType(), participant.getOwnerId(),
					namesById.get(participant.getOwnerId()));
		}
	}

	@Getter @AllArgsConstructor
	public static class ConversationResponse {
		private UUID id;
		private ConversationType type;
		private List<ParticipantResponse> participants;
		@Schema(description = "Newest message, for the list preview - null when the chat has no messages yet")
		private LastMessageResponse lastMessage;
		@Schema(description = "Messages from others since the caller last opened this chat (POST .../read)")
		private long unreadCount;

		public static ConversationResponse from(Conversation conversation, List<ConversationParticipant> participants,
				Map<UUID, String> namesById) {
			return from(conversation, participants, namesById, null, 0);
		}

		public static ConversationResponse from(Conversation conversation, List<ConversationParticipant> participants,
				Map<UUID, String> namesById, Message lastMessage, long unreadCount) {
			return new ConversationResponse(
					conversation.getId(),
					conversation.getType(),
					participants.stream().map(p -> ParticipantResponse.from(p, namesById)).toList(),
					lastMessage == null ? null : LastMessageResponse.from(lastMessage),
					unreadCount);
		}
	}

	@Getter @AllArgsConstructor
	@Schema(description = "A conversation's newest message, trimmed for the chat list (no attachment URL - open "
			+ "the conversation for that)")
	public static class LastMessageResponse {
		static final int PREVIEW_MAX = 200;

		private UUID id;
		private SenderKind senderKind;
		private OwnerType senderOwnerType;
		private UUID senderOwnerId;
		@Schema(description = "First 200 characters; null for an attachment with no caption")
		private String content;
		private String attachmentContentType;
		private String attachmentFileName;
		private Instant sentAt;

		public static LastMessageResponse from(Message message) {
			String content = message.getContent();
			if (content != null && content.length() > PREVIEW_MAX) {
				content = content.substring(0, PREVIEW_MAX);
			}
			return new LastMessageResponse(message.getId(), message.getSenderKind(), message.getSenderOwnerType(),
					message.getSenderOwnerId(), content, message.getAttachmentContentType(),
					message.getAttachmentFileName(), message.getSentAt());
		}
	}

	@Getter @AllArgsConstructor
	public static class UnreadCountResponse {
		private long unread;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Someone the caller may start a 1:1 chat with, and why. For a parent: their children's "
			+ "teachers and the school's admins. For staff: parents of their students (any parent, for an admin).")
	public static class ContactResponse {
		private OwnerType ownerType;
		private UUID ownerId;
		private String name;
		@Schema(description = "Staff contact only: this person is a school admin")
		private boolean admin;
		@Schema(description = "Staff contact only: the parent's children's sections (e.g. \"5 - A\") this person is class teacher of")
		private List<String> classTeacherOf;
		@Schema(description = "Staff contact only: \"Subject (section)\" this person teaches the parent's children")
		private List<String> subjects;
		@Schema(description = "Parent contact only: \"Child (section)\" for each of their children the caller teaches")
		private List<String> children;
	}

	@Getter @Setter
	@Schema(description = "content and attachmentObjectKey are each optional, but at least one is "
			+ "required - an image/PDF can be sent with or without a caption")
	public static class SendMessageRequest {
		private String content;
		@Schema(description = "The objectKey returned by the presign call, once the upload to S3 has completed")
		private String attachmentObjectKey;
		private String attachmentContentType;
		private String attachmentFileName;
	}

	@Getter @Setter
	@Schema(description = "Request a presigned S3 upload slot for one attachment before sending the message")
	public static class PresignAttachmentRequest {
		@NotBlank private String fileName;
		@NotBlank private String contentType;
		@NotNull @Positive private Long fileSizeBytes;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "uploadUrl is a presigned PUT - upload the raw file bytes there directly, "
			+ "then send the message with objectKey/contentType/fileName")
	public static class PresignAttachmentResponse {
		private String uploadUrl;
		private String objectKey;
		private Instant expiresAt;
	}

	@Getter @AllArgsConstructor
	public static class MessageResponse {
		private UUID id;
		private SenderKind senderKind;
		private OwnerType senderOwnerType;
		private UUID senderOwnerId;
		private String content;
		@Schema(description = "Presigned GET URL for the attachment, if any - freshly signed on every "
				+ "read so it never appears expired, even for old messages")
		private String attachmentUrl;
		private String attachmentContentType;
		private String attachmentFileName;
		private Instant sentAt;

		public static MessageResponse from(Message message, String attachmentUrl) {
			return new MessageResponse(
					message.getId(),
					message.getSenderKind(),
					message.getSenderOwnerType(),
					message.getSenderOwnerId(),
					message.getContent(),
					attachmentUrl,
					message.getAttachmentContentType(),
					message.getAttachmentFileName(),
					message.getSentAt());
		}
	}

	@Getter @AllArgsConstructor
	public static class MessagePageResponse {
		private List<MessageResponse> messages;
		private boolean hasMore;
	}

}
