package com.gurukul.gamification.dto;

import com.gurukul.gamification.entity.House;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class HouseDtos {

	@Getter @Setter
	@Schema(name = "CreateHouseRequest")
	public static class CreateHouseRequest {
		@NotBlank private String name;
		@NotBlank
		@Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorHex must look like #RRGGBB")
		private String colorHex;
	}

	@Getter @Setter
	@Schema(name = "AwardSpotRecognitionRequest")
	public static class AwardSpotRecognitionRequest {
		@NotNull private UUID studentId;
		@NotNull @Min(1) @Max(20) private Integer amount;
		@NotBlank private String reason;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "HouseResponse")
	public static class HouseResponse {
		private UUID id;
		private String name;
		private String colorHex;

		public static HouseResponse from(House house) {
			return new HouseResponse(house.getId(), house.getName(), house.getColorHex());
		}
	}

	@Getter @AllArgsConstructor
	@Schema(name = "HouseStandingResponse")
	public static class HouseStandingResponse {
		private UUID houseId;
		private String name;
		private String colorHex;
		private long totalPoints;
		private long memberCount;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "SpotRecognitionFeedItem")
	public static class SpotRecognitionFeedItem {
		private String studentName;
		private String houseName;
		private int amount;
		private String reason;
		private Instant occurredAt;
	}

	@Getter @AllArgsConstructor
	@Schema(name = "HouseWarsResponse")
	public static class HouseWarsResponse {
		private List<HouseStandingResponse> standings;
		private List<SpotRecognitionFeedItem> recentFeed;
		private UUID yourHouseId;
	}

}
