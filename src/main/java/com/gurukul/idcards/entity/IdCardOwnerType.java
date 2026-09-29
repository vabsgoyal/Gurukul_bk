package com.gurukul.idcards.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whose ID card: a student or a staff member (employee)")
public enum IdCardOwnerType {

	STUDENT("S", "student"),
	EMPLOYEE("E", "employee");

	/** One-letter tag inside the QR code. */
	private final String code;
	/** Path segment for the S3 photo prefix. */
	private final String pathSegment;

	IdCardOwnerType(String code, String pathSegment) {
		this.code = code;
		this.pathSegment = pathSegment;
	}

	public String code() {
		return code;
	}

	public String pathSegment() {
		return pathSegment;
	}

	public static IdCardOwnerType fromCode(String code) {
		for (IdCardOwnerType type : values()) {
			if (type.code.equals(code)) {
				return type;
			}
		}
		return null;
	}

}
