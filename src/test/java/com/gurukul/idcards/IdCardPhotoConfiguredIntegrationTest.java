package com.gurukul.idcards;

import com.gurukul.auth.AuthTestSupport;
import com.gurukul.auth.entity.Credential;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

/** Profile-photo upload against a mocked S3 (bucket "configured"), through to the card PDF. */
@SpringBootTest(properties = "app.chat.attachments.bucket=test-photo-bucket")
@AutoConfigureMockMvc
class IdCardPhotoConfiguredIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private S3Presigner s3Presigner;
	@MockitoBean
	private S3Client s3Client;

	@Test
	void aStudentUploadsAPhotoAndTheCardEmbedsIt() throws Exception {
		PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
		when(presignedPut.url()).thenReturn(URI.create("https://test-photo-bucket.s3.example/upload").toURL());
		when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);
		PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
		when(presignedGet.url()).thenReturn(URI.create("https://test-photo-bucket.s3.example/photo.png").toURL());
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(20_000L).build());
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/png").build(), photo()));

		String section = createSection();
		String student = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Photo Student");
		String other = AuthTestSupport.createStudent(mockMvc, SCHOOL_ID, section, "Other Student");
		String token = token(student);
		String base = "/api/v1/id-cards/students/" + student + "/photo";

		// Only PNG/JPEG, max 3 MB.
		mockMvc.perform(as(post(base + "/presign"), token).content("{\"contentType\": \"image/gif\", \"fileSizeBytes\": 2000}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(as(post(base + "/presign"), token).content("{\"contentType\": \"image/jpeg\", \"fileSizeBytes\": 4000000}"))
				.andExpect(status().isBadRequest());

		String presign = mockMvc.perform(as(post(base + "/presign"), token)
						.content("{\"contentType\": \"image/png\", \"fileSizeBytes\": 20000}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String objectKey = JsonPath.read(presign, "$.data.objectKey");
		assertThat(objectKey).startsWith("profile-photos/" + SCHOOL_ID + "/student/" + student + "/").endsWith(".png");

		// A key for someone else (another student, a staff prefix, a school logo) is refused.
		for (String foreign : new String[] {
				"profile-photos/" + SCHOOL_ID + "/student/" + other + "/x.png",
				"profile-photos/" + SCHOOL_ID + "/employee/" + student + "/x.png",
				"school-logos/" + SCHOOL_ID + "/x.png",
				"profile-photos/" + SCHOOL_ID + "/student/" + student + "/../../" + other + "/x.png"}) {
			mockMvc.perform(as(put(base), token).content("{\"objectKey\": \"" + foreign + "\"}"))
					.andExpect(status().isBadRequest());
		}

		mockMvc.perform(as(put(base), token).content("{\"objectKey\": \"" + objectKey + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPhoto").value(true))
				.andExpect(jsonPath("$.data.photoUrl").value("https://test-photo-bucket.s3.example/photo.png"))
				.andExpect(jsonPath("$.data.missing[0]").value("BLOOD_GROUP"));

		byte[] pdf = mockMvc.perform(as(get("/api/v1/id-cards/students/" + student + "/card.pdf"), token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = Loader.loadPDF(pdf)) {
			// Logo + photo + QR are embedded images (no photo would mean logo + QR only).
			long images = 0;
			var resources = document.getPage(0).getResources();
			for (var name : resources.getXObjectNames()) {
				if (resources.getXObject(name) instanceof PDImageXObject) {
					images++;
				}
			}
			assertThat(images).isGreaterThanOrEqualTo(3);
		}

		mockMvc.perform(as(delete(base), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPhoto").value(false))
				.andExpect(jsonPath("$.data.photoUrl").doesNotExist());
	}

	private String token(String studentId) {
		Credential credential = new Credential();
		credential.setSchoolId(UUID.fromString(SCHOOL_ID));
		credential.setOwnerType(OwnerType.STUDENT);
		credential.setOwnerId(UUID.fromString(studentId));
		credential.setRole(Role.STUDENT);
		credential.setUsername("photo-" + UUID.randomUUID().toString().substring(0, 8));
		return jwtService.generateToken(credential);
	}

	private static byte[] photo() throws Exception {
		BufferedImage image = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private String createSection() throws Exception {
		String response = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", SCHOOL_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 4", "section": "PH-%s", "academicYear": "2026-27"}
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
