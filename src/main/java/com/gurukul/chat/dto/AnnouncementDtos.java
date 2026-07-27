package com.gurukul.chat.dto;

import com.gurukul.chat.entity.Announcement;
import com.gurukul.chat.entity.AnnouncementScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

public class AnnouncementDtos {

	@Getter @Setter
	public static class CreateAnnouncementRequest {
		@NotNull private AnnouncementScope scope;
		private UUID sectionId;
		@NotBlank private String title;
		@NotBlank private String body;
	}

	@Getter @AllArgsConstructor
	public static class AnnouncementResponse {
		private UUID id;
		private AnnouncementScope scope;
		private UUID sectionId;
		private UUID authorEmployeeId;
		private String title;
		private String body;
		private Instant createdAt;

		public static AnnouncementResponse from(Announcement announcement) {
			return new AnnouncementResponse(
					announcement.getId(),
					announcement.getScope(),
					announcement.getSectionId(),
					announcement.getAuthorEmployeeId(),
					announcement.getTitle(),
					announcement.getBody(),
					announcement.getCreatedAt());
		}
	}

}
