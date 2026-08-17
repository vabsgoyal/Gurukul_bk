package com.gurukul.calls.googlemeet;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** One teacher's connected Google account - encryptedRefreshToken lets the backend mint fresh
 *  access tokens on demand to create Meet links on this teacher's behalf, without them needing to
 *  re-authorize every time. */
@Getter
@Setter
@Entity
@Table(name = "teacher_google_credential")
public class TeacherGoogleCredential extends BaseEntity {

	@Column(name = "employee_id", nullable = false, unique = true)
	private UUID employeeId;

	@Column(name = "google_email", nullable = false)
	private String googleEmail;

	@Column(name = "encrypted_refresh_token", nullable = false)
	private String encryptedRefreshToken;

}
