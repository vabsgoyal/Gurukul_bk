package com.gurukul.schools;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logo happy path against a mocked S3 (bucket "configured"), through to the report-card PDF. */
@SpringBootTest(properties = "app.chat.attachments.bucket=test-logo-bucket")
@AutoConfigureMockMvc
class SchoolLogoConfiguredIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private S3Presigner s3Presigner;

	@MockitoBean
	private S3Client s3Client;

	@Test
	void adminUploadsConfirmsAndThePdfUsesTheLogo() throws Exception {
		PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
		when(presignedPut.url()).thenReturn(URI.create("https://test-logo-bucket.s3.example/upload").toURL());
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);
		PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
		when(presignedGet.url()).thenReturn(URI.create("https://test-logo-bucket.s3.example/logo.png").toURL());
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(20_000L).build());
		byte[] png;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("pdf/gurukul-mark.png")) {
			png = in.readAllBytes();
		}
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), png));

		String adminToken = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		try {
			// Only PNG/JPEG, max 2 MB.
			mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), adminToken)
							.content("{\"contentType\": \"image/webp\", \"fileSizeBytes\": 20000}"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), adminToken)
							.content("{\"contentType\": \"image/png\", \"fileSizeBytes\": 5000000}"))
					.andExpect(status().isBadRequest());

			String presign = mockMvc.perform(as(post("/api/v1/schools/" + SCHOOL_ID + "/logo/presign"), adminToken)
							.content("{\"contentType\": \"image/png\", \"fileSizeBytes\": 20000}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.uploadUrl").value("https://test-logo-bucket.s3.example/upload"))
					.andReturn().getResponse().getContentAsString();
			String objectKey = JsonPath.read(presign, "$.data.objectKey");
			assertThat(objectKey).startsWith("school-logos/" + SCHOOL_ID + "/").endsWith(".png");

			// A key presigned for some other school (or any other prefix) is refused.
			mockMvc.perform(as(put("/api/v1/schools/" + SCHOOL_ID + "/logo"), adminToken)
							.content("{\"objectKey\": \"school-logos/" + UUID.randomUUID() + "/evil.png\"}"))
					.andExpect(status().isBadRequest());
			mockMvc.perform(as(put("/api/v1/schools/" + SCHOOL_ID + "/logo"), adminToken)
							.content("{\"objectKey\": \"chat-attachments/" + SCHOOL_ID + "/x.png\"}"))
					.andExpect(status().isBadRequest());

			mockMvc.perform(as(put("/api/v1/schools/" + SCHOOL_ID + "/logo"), adminToken)
							.content("{\"objectKey\": \"" + objectKey + "\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.logoUrl").value("https://test-logo-bucket.s3.example/logo.png"));

			// The PDF now shows the school's own logo instead of the "Generated by Gurukul" placeholder.
			String sectionId = createSection();
			String studentId = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, sectionId, "Logo Student");
			byte[] pdf = mockMvc.perform(as(get("/api/v1/students/" + studentId + "/report-card.pdf"), adminToken)
							.param("term", "Term 1"))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsByteArray();
			try (PDDocument document = Loader.loadPDF(pdf)) {
				assertThat(new PDFTextStripper().getText(document)).contains("Logo Student").doesNotContain("Generated by Gurukul");
			}
		} finally {
			mockMvc.perform(as(delete("/api/v1/schools/" + SCHOOL_ID + "/logo"), adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.logoUrl").doesNotExist());
		}
	}

	private String createSection() throws Exception {
		String response = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 5", "section": "LOGO-%s", "academicYear": "2026-27"}
								""".formatted(UUID.randomUUID().toString().substring(0, 8))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.id");
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
		return request
				.header("X-School-Id", SCHOOL_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON);
	}

}
