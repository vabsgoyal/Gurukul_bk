package com.gurukul.schools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Sets the school's geolocation and self-attendance geofence radius")
public class SchoolLocationUpdateRequest {

	@NotNull
	@Min(-90) @Max(90)
	@Schema(description = "Latitude in decimal degrees", example = "26.9124")
	private Double latitude;

	@NotNull
	@Min(-180) @Max(180)
	@Schema(description = "Longitude in decimal degrees", example = "75.7873")
	private Double longitude;

	@NotNull
	@Min(1)
	@Schema(description = "Radius in meters within which a teacher may self-mark attendance", example = "100")
	private Integer geofenceRadiusMeters;

}
