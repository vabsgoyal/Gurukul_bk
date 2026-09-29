package com.gurukul.admissions.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A supporting document for an application. Only the private S3 object key is stored - never a URL. */
@Getter
@Setter
@Entity
@Table(name = "admission_document")
public class AdmissionDocument extends BaseEntity {

	@Column(name = "application_id", nullable = false)
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false)
	private AdmissionDocumentType documentType;

	@Column(name = "object_key", nullable = false)
	private String objectKey;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "file_size_bytes", nullable = false)
	private long fileSizeBytes;

}
