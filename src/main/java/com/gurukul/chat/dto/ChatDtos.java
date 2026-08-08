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

		public static ParticipantResponse from(ConversationParticipant participant) {
			return new ParticipantResponse(participant.getOwnerType(), participant.getOwnerId());
		}
	}

	@Getter @AllArgsConstructor
	public static class ConversationResponse {
		private UUID id;
		private ConversationType type;
		private List<ParticipantResponse> participants;

		public static ConversationResponse from(Conversation conversation, List<ConversationParticipant> participants) {
			return new ConversationResponse(
					conversation.getId(),
					conversation.getType(),
					participants.stream().map(ParticipantResponse::from).toList());
		}
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
