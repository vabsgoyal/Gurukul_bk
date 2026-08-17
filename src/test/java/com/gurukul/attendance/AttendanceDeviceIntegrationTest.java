package com.gurukul.attendance;

import com.gurukul.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: register a device, enroll a student (RFID) and an employee (FINGERPRINT) against it,
 * then drive the device-event endpoint exactly as a real reader/scanner would - via its issued API
 * key, never a user JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceDeviceIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private String registerSchool(String namePrefix) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/schools")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "%s School",
								  "address": "1 Device Test Street",
								  "city": "Jaipur",
								  "state": "Rajasthan",
								  "pincode": "302001",
								  "contactEmail": "office@%s.example",
								  "contactPhone": "9222222222",
								  "principalName": "Dr. Device Principal",
								  "directorName": "Mr. Device Director",
								  "principalPhone": "9222222222",
								  "adminPhone": "8222222222"
								}
								""".formatted(namePrefix, namePrefix.toLowerCase())))
				.andExpect(status().isOk())
				.andReturn();
		return result.getResponse().getContentAsString();
	}

	@Test
	void deviceEventMarksEnrolledStudentAndEmployeePresent() throws Exception {
		String schoolJson = registerSchool("DeviceTest");
		String schoolId = JsonPath.read(schoolJson, "$.data.school.id");
		String adminBearer = JsonPath.read(schoolJson, "$.data.principal.token");

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 5", "section": "Device", "academicYear": "2026-27"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
		String studentId = AuthTestSupport.createStudent(mockMvc, schoolId, sectionId, "RFID Student");
		String employeeId = AuthTestSupport.createEmployee(mockMvc, schoolId, "Fingerprint Teacher");

		// Register an RFID device and a FINGERPRINT device.
		MvcResult rfidDeviceResult = mockMvc.perform(post("/api/v1/attendance-devices")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Main Gate RFID", "deviceType": "RFID"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.apiKey").exists())
				.andReturn();
		String rfidDeviceId = JsonPath.read(rfidDeviceResult.getResponse().getContentAsString(), "$.data.id");
		String rfidKey = JsonPath.read(rfidDeviceResult.getResponse().getContentAsString(), "$.data.apiKey");

		MvcResult fingerprintDeviceResult = mockMvc.perform(post("/api/v1/attendance-devices")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Staff Room Fingerprint", "deviceType": "FINGERPRINT"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String fingerprintKey = JsonPath.read(fingerprintDeviceResult.getResponse().getContentAsString(), "$.data.apiKey");

		// Enroll the student's RFID card and the employee's fingerprint.
		mockMvc.perform(post("/api/v1/students/" + studentId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "RFID", "externalId": "CARD-0001"}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/employees/" + employeeId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "FINGERPRINT", "externalId": "FP-0001"}
								"""))
				.andExpect(status().isOk());

		// A scan for the student's card marks the student present via the device.
		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", rfidKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "CARD-0001"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerType").value("STUDENT"))
				.andExpect(jsonPath("$.data.status").value("PRESENT"));

		mockMvc.perform(get("/api/v1/students/" + studentId + "/attendance")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.records[0].method").value("RFID"))
				.andExpect(jsonPath("$.data.records[0].markedByTeacherId").value(nullValue()));

		// A scan for the employee's fingerprint marks the employee present via the device.
		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", fingerprintKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "FP-0001"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ownerType").value("EMPLOYEE"))
				.andExpect(jsonPath("$.data.status").value("PRESENT"));

		// Wrong/garbage device key is rejected.
		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", "adk_not-a-real-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "CARD-0001"}
								"""))
				.andExpect(status().isUnauthorized());

		// An externalId nobody enrolled is rejected.
		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", rfidKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "CARD-NEVER-ENROLLED"}
								"""))
				.andExpect(status().isNotFound());

		// Rotating the key invalidates the old one and the new one works.
		MvcResult rotateResult = mockMvc.perform(post("/api/v1/attendance-devices/" + rfidDeviceId + "/rotate-key")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String rotatedKey = JsonPath.read(rotateResult.getResponse().getContentAsString(), "$.data.apiKey");

		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", rfidKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "CARD-0001"}
								"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolId)
						.header("X-Device-Key", rotatedKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "CARD-0001"}
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void deviceKeyFromAnotherSchoolIsRejectedForCrossSchoolIsolation() throws Exception {
		String schoolAJson = registerSchool("DeviceIsoA");
		String schoolAId = JsonPath.read(schoolAJson, "$.data.school.id");
		String schoolABearer = JsonPath.read(schoolAJson, "$.data.principal.token");

		String schoolBJson = registerSchool("DeviceIsoB");
		String schoolBId = JsonPath.read(schoolBJson, "$.data.school.id");
		String schoolBBearer = JsonPath.read(schoolBJson, "$.data.principal.token");

		MvcResult deviceBResult = mockMvc.perform(post("/api/v1/attendance-devices")
						.header("X-School-Id", schoolBId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolBBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "School B Gate", "deviceType": "RFID"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String schoolBDeviceKey = JsonPath.read(deviceBResult.getResponse().getContentAsString(), "$.data.apiKey");

		// School B's key presented under School A's X-School-Id must not resolve to any device.
		mockMvc.perform(post("/api/v1/attendance/device-events")
						.header("X-School-Id", schoolAId)
						.header("X-Device-Key", schoolBDeviceKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"externalId": "ANY-CARD"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(containsString("Invalid")));

		// Sanity: unrelated admin token still can't do anything cross-school either (existing pattern).
		mockMvc.perform(get("/api/v1/attendance-devices")
						.header("X-School-Id", schoolAId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolABearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void enrollingTheSameIdentifierTwiceIsRejected() throws Exception {
		String schoolJson = registerSchool("DeviceDup");
		String schoolId = JsonPath.read(schoolJson, "$.data.school.id");
		String adminBearer = JsonPath.read(schoolJson, "$.data.principal.token");

		MvcResult sectionResult = mockMvc.perform(post("/api/v1/class-sections")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"className": "Grade 6", "section": "Dup", "academicYear": "2026-27"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String sectionId = JsonPath.read(sectionResult.getResponse().getContentAsString(), "$.data.id");
		String studentOneId = AuthTestSupport.createStudent(mockMvc, schoolId, sectionId, "Dup Student One");
		String studentTwoId = AuthTestSupport.createStudent(mockMvc, schoolId, sectionId, "Dup Student Two");

		mockMvc.perform(post("/api/v1/students/" + studentOneId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "RFID", "externalId": "CARD-DUP"}
								"""))
				.andExpect(status().isOk());

		// Same external id, different student -> rejected.
		mockMvc.perform(post("/api/v1/students/" + studentTwoId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "RFID", "externalId": "CARD-DUP"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("already enrolled")));

		// A second RFID identifier for the same student -> also rejected.
		mockMvc.perform(post("/api/v1/students/" + studentOneId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "RFID", "externalId": "CARD-OTHER"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("already has an active identifier")));

		// Removing the identifier frees the external id back up.
		MvcResult listResult = mockMvc.perform(get("/api/v1/students/" + studentOneId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk())
				.andReturn();
		String identifierId = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data[0].id");

		mockMvc.perform(delete("/api/v1/attendance-identifiers/" + identifierId)
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/students/" + studentTwoId + "/attendance-identifiers")
						.header("X-School-Id", schoolId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"method": "RFID", "externalId": "CARD-DUP"}
								"""))
				.andExpect(status().isOk());
	}

}
