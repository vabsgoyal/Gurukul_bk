package com.gurukul.fees;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeePaymentRequestResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.FeePaymentRepository;
import com.gurukul.fees.repository.PaymentAttemptRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.finance.repository.FinancialTransactionRepository;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.fees.service.FeePaymentService;
import com.gurukul.parents.service.ParentService;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.students.entity.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for the STUDENT self-only ownership check added to
 * {@link FeePaymentService#createPaymentRequest} / {@code recordPayment}. Uses Mockito directly
 * (no MockMvc/JWT) since nothing else in this codebase's tests authenticates as a specific role -
 * simulating the SecurityContext directly is the simplest way to exercise this branch.
 */
class FeePaymentServiceOwnershipTest {

	private static final UUID SCHOOL_ID = UUID.randomUUID();
	private static final UUID OWNING_STUDENT_ID = UUID.randomUUID();
	private static final UUID OTHER_STUDENT_ID = UUID.randomUUID();
	private static final UUID ASSESSMENT_ID = UUID.randomUUID();

	private StudentFeeAssessmentRepository assessmentRepository;
	private SchoolRepository schoolRepository;
	private FeePaymentService feePaymentService;

	@BeforeEach
	void setUp() {
		assessmentRepository = mock(StudentFeeAssessmentRepository.class);
		schoolRepository = mock(SchoolRepository.class);
		SchoolContext schoolContext = new SchoolContext();
		schoolContext.setSchoolId(SCHOOL_ID);

		feePaymentService = new FeePaymentService(
				assessmentRepository,
				mock(FeePaymentRepository.class),
				mock(FinancialTransactionRepository.class),
				mock(LedgerService.class),
				schoolContext,
				mock(ParentService.class),
				schoolRepository,
				mock(PaymentAttemptRepository.class));

		Student student = new Student();
		student.setId(OWNING_STUDENT_ID);
		student.setName("Test Student");

		StudentFeeAssessment assessment = new StudentFeeAssessment();
		assessment.setId(ASSESSMENT_ID);
		assessment.setSchoolId(SCHOOL_ID);
		assessment.setStudent(student);
		assessment.setAcademicYear("2026-27");
		assessment.setTotalDue(new BigDecimal("10000.00"));
		assessment.setTotalPaid(BigDecimal.ZERO);
		assessment.setStatus(FeeAssessmentStatus.UNPAID);
		when(assessmentRepository.findByIdAndSchoolId(ASSESSMENT_ID, SCHOOL_ID)).thenReturn(Optional.of(assessment));

		School school = new School();
		school.setId(SCHOOL_ID);
		school.setName("Test School");
		school.setBankAccountNumber("123456789012");
		school.setBankIfsc("SBIN0001234");
		when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Optional.of(school));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void authenticateAs(UUID ownerId, Role role, OwnerType ownerType) {
		AuthPrincipal principal = new AuthPrincipal(ownerId, ownerType, role, SCHOOL_ID, "test-user");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
	}

	@Test
	void studentCanCreatePaymentRequestForOwnAssessment() {
		authenticateAs(OWNING_STUDENT_ID, Role.STUDENT, OwnerType.STUDENT);

		FeePaymentRequestResponse response = feePaymentService.createPaymentRequest(ASSESSMENT_ID);

		assertThat(response.getAmount()).isEqualByComparingTo("10000.00");
		assertThat(response.getUpiUri()).startsWith("upi://pay?pa=123456789012%40sbi");
		assertThat(response.getReferenceId()).startsWith("FEE");
	}

	@Test
	void studentCannotCreatePaymentRequestForSomeoneElsesAssessment() {
		authenticateAs(OTHER_STUDENT_ID, Role.STUDENT, OwnerType.STUDENT);

		assertThatThrownBy(() -> feePaymentService.createPaymentRequest(ASSESSMENT_ID))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void unauthenticatedCallerIsUnaffectedByOwnershipCheck() {
		// No SecurityContext set up - mirrors this codebase's existing (pre-existing gap) tests that
		// call fee endpoints without an Authorization header.
		FeePaymentRequestResponse response = feePaymentService.createPaymentRequest(ASSESSMENT_ID);

		assertThat(response.getAssessmentId()).isEqualTo(ASSESSMENT_ID);
	}

}
