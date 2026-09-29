package com.gurukul.common.storage;

import com.gurukul.chat.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3PresignHelperTest {

	private static final AttachmentProperties CONFIGURED = new AttachmentProperties(
			"test-bucket", "eu-north-1", 1024, "image/png,application/pdf", 300, 3600);

	private static S3Presigner presigner() {
		// Static fake credentials: presigning is pure local signing, no network call is made.
		return S3Presigner.builder()
				.region(Region.EU_NORTH_1)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDEXAMPLE", "secret")))
				.build();
	}

	@Test
	void sanitizesFileNames() {
		assertThat(S3PresignHelper.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(S3PresignHelper.sanitizeFileName("birth cert (1).pdf")).isEqualTo("birth_cert__1_.pdf");
	}

	@Test
	void validatesTypeAndSize() {
		S3PresignHelper helper = new S3PresignHelper(CONFIGURED, presigner());
		helper.validateUpload("application/pdf", 1024);
		assertThatThrownBy(() -> helper.validateUpload("text/html", 10)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported file type");
		assertThatThrownBy(() -> helper.validateUpload("image/png", 1025)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("too large");
	}

	@Test
	void presignsPrivateTimeLimitedUrlsForTheGivenKey() {
		S3PresignHelper helper = new S3PresignHelper(CONFIGURED, presigner());
		assertThat(helper.isConfigured()).isTrue();

		S3PresignHelper.PresignedUpload upload = helper.presignUpload("admissions/s/a/x-birth.pdf", "application/pdf", 100);
		assertThat(upload.objectKey()).isEqualTo("admissions/s/a/x-birth.pdf");
		assertThat(upload.uploadUrl()).contains("test-bucket").contains("admissions/s/a/x-birth.pdf").contains("X-Amz-Expires=300");

		assertThat(helper.presignDownload("admissions/s/a/x-birth.pdf")).contains("X-Amz-Expires=3600");
	}

	@Test
	void reportsUnconfiguredBucket() {
		S3PresignHelper helper = new S3PresignHelper(
				new AttachmentProperties("", "eu-north-1", 1024, "image/png", 300, 3600), presigner());
		assertThat(helper.isConfigured()).isFalse();
	}

}
