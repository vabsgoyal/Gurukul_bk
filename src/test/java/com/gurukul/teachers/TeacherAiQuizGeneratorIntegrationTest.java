package com.gurukul.teachers;

import com.gurukul.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeacherAiQuizGeneratorIntegrationTest {

	private static final String SCHOOL_ID = "11111111-1111-1111-1111-111111111111";
	private static final String TEACHER_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
	private static final String CLASS_SECTION_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generateQuizFromSyllabus() throws Exception {
		String bearer = AuthTestSupport.loginAsDevAdmin(mockMvc, SCHOOL_ID);
		String body = """
				{
				  "classSectionId": "%s",
				  "subjectName": "Science",
				  "assessmentType": "QUIZ",
				  "title": "Photosynthesis Quick Check",
				  "syllabus": "Photosynthesis, chlorophyll, stomata",
				  "difficulty": "MEDIUM",
				  "questionCount": 5,
				  "maxMarks": 10,
				  "questionTypes": ["MCQ", "SHORT_ANSWER"],
				  "additionalInstructions": "Keep questions age appropriate."
				}
				""".formatted(CLASS_SECTION_A);

		mockMvc.perform(post("/api/v1/teachers/" + TEACHER_ID + "/ai/quiz-generator")
						.header("X-School-Id", SCHOOL_ID)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Quiz generated"))
				.andExpect(jsonPath("$.data.title").value("Photosynthesis Quick Check"))
				.andExpect(jsonPath("$.data.generatorMode").value("LOCAL_DETERMINISTIC_GENERATOR"))
				.andExpect(jsonPath("$.data.questions.length()").value(5))
				.andExpect(jsonPath("$.data.questions[0].questionType").value("MCQ"))
				.andExpect(jsonPath("$.data.questions[1].questionType").value("SHORT_ANSWER"));
	}

	@Test
	void teacherDashboardIncludesAiQuizGeneratorFeature() throws Exception {
		mockMvc.perform(get("/api/v1/teachers/dashboard").header("X-School-Id", SCHOOL_ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.features[?(@.feature == 'AI_QUIZ_GENERATOR' && @.availableInCurrentSlice == true)]").exists());
	}

}
