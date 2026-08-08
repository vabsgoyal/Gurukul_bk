package com.gurukul.fees.dto;

import com.gurukul.fees.entity.FeePayment;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.schools.entity.School;
import com.gurukul.students.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Fee payment record - includes everything needed to render a receipt")
public class FeePaymentResponse {

	private UUID id;
	private UUID schoolId;
	private UUID assessmentId;
	private UUID studentId;
	private BigDecimal amount;
	private UUID transactionId;
	private String receiptNumber;
	private String studentName;
	private String rollNumber;
	private String classSectionLabel;
	private String academicYear;
	private String schoolName;
	private PaymentMethod paymentMethod;
	private String paymentReference;
	private LocalDate transactionDate;
	private Instant createdAt;
	private Instant updatedAt;

	public static FeePaymentResponse from(FeePayment payment, FinancialTransaction transaction, School school) {
		Student student = payment.getAssessment().getStudent();
		return new FeePaymentResponse(
				payment.getId(),
				payment.getSchoolId(),
				payment.getAssessment().getId(),
				student.getId(),
				payment.getAmount(),
				payment.getTransactionId(),
				transaction.getReceiptNumber(),
				student.getName(),
				student.getRollNumber(),
				student.getClassSection().getDisplayLabel(),
				payment.getAssessment().getAcademicYear(),
				school.getName(),
				transaction.getPaymentMethod(),
				transaction.getPaymentReference(),
				transaction.getTransactionDate(),
				payment.getCreatedAt(),
				payment.getUpdatedAt()
		);
	}

}
