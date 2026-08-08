package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Teacher profile payload")
public class TeacherRequest {

	@NotBlank
	@Schema(description = "Unique employee code within the school", example = "T-1001")
	private String employeeCode;

	@NotBlank
	@Schema(description = "Full name", example = "Anita Verma")
	private String name;

	@NotBlank
	@Email
	@Schema(description = "School email address", example = "anita.verma@gurukul.demo")
	private String email;

	@NotBlank
	@Schema(description = "Primary phone number", example = "9876543210")
	private String phone;

	@NotBlank
	@Schema(description = "Highest qualification", example = "M.Sc. Mathematics, B.Ed.")
	private String qualification;

	@NotBlank
	@Schema(description = "Main teaching specialization", example = "Mathematics")
	private String specialization;

	@NotNull
	@PastOrPresent
	@Schema(description = "Joining date", example = "2024-04-01")
	private LocalDate joiningDate;

	@Schema(description = "Lifecycle status. Defaults to ACTIVE on create.", example = "ACTIVE")
	private TeacherStatus status;

}
