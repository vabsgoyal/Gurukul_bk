package com.gurukul.leads.dto;

import com.gurukul.leads.entity.LeadType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class LeadDtos {

	@Getter @Setter @NoArgsConstructor
	@Schema(description = "A demo or website-services request from the marketing site")
	public static class CreateLeadRequest {
		@NotBlank @Size(max = 100)
		private String name;

		@NotBlank @Size(max = 150)
		private String schoolName;

		@Size(max = 60)
		@Schema(example = "Principal")
		private String role;

		/** Indian mobile: 10 digits starting 6-9, optionally prefixed with +91, 91 or 0 (spaces/dashes allowed). */
		@NotBlank
		@Pattern(regexp = "^(?:\\+?91[\\s-]?|0)?[6-9]\\d{4}[\\s-]?\\d{5}$", message = "must be a valid Indian mobile number")
		private String phone;

		@Email @Size(max = 150)
		private String email;

		@Size(max = 80)
		private String city;

		@Size(max = 80)
		private String state;

		@Size(max = 20)
		@Schema(example = "200-500")
		private String studentCount;

		@Size(max = 1000)
		private String message;

		@Schema(description = "What is being asked for; DEMO when omitted", example = "WEBSITE_SERVICES")
		private LeadType requestType;

		@Size(max = 10)
		@Schema(description = "Website services wanted (WEBSITE_SERVICES only)", example = "[\"New school website\", \"Online admission form\"]")
		private List<@Size(max = 40) String> services;

		@Size(max = 40)
		@Schema(example = "₹15,000 - ₹30,000")
		private String budget;

		@Size(max = 200)
		@Schema(description = "Path of the page the form was submitted from", example = "/gps-attendance.html")
		private String sourcePage;

		/** Honeypot: hidden on the real form, so any value means a bot. */
		@Size(max = 200)
		@Schema(description = "Leave empty (anti-spam honeypot)")
		private String website;
	}

	@Getter @AllArgsConstructor
	public static class LeadReceived {
		private String status;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "A stored demo lead (founders / Sales Desk only)")
	public static class LeadResponse {
		private UUID id;
		private String name;
		private String schoolName;
		private String role;
		private String phone;
		private String email;
		private String city;
		private String state;
		private String studentCount;
		private String message;
		private LeadType requestType;
		private String services;
		private String budget;
		private String sourcePage;
		private Instant createdAt;
	}

}
