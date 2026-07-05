package com.gurukul.finance.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.documents.DocumentNumberGenerator;
import com.gurukul.finance.dto.FinancialTransactionResponse;
import com.gurukul.finance.dto.FundSummaryResponse;
import com.gurukul.finance.dto.ManualTransactionRequest;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.finance.entity.ReceiptSequenceType;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.entity.TransactionDirection;
import com.gurukul.finance.entity.TransactionStatus;
import com.gurukul.finance.repository.FinancialTransactionRepository;
import com.gurukul.finance.repository.FundAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

	private static final String DEFAULT_ACADEMIC_YEAR = "2026-27";

	private final FinancialTransactionRepository transactionRepository;
	private final FundAccountRepository fundAccountRepository;
	private final DocumentNumberGenerator documentNumberGenerator;
	private final SchoolContext schoolContext;

	public List<FinancialTransactionResponse> listTransactions() {
		return transactionRepository
				.findAllBySchoolIdOrderByTransactionDateDescCreatedAtDesc(schoolContext.getSchoolId())
				.stream()
				.map(FinancialTransactionResponse::from)
				.toList();
	}

	public FundSummaryResponse getSummary() {
		UUID schoolId = schoolContext.getSchoolId();
		BigDecimal inflow = transactionRepository.sumBySchoolIdAndDirectionAndStatus(
				schoolId, TransactionDirection.INFLOW, TransactionStatus.COMPLETED);
		BigDecimal outflow = transactionRepository.sumBySchoolIdAndDirectionAndStatus(
				schoolId, TransactionDirection.OUTFLOW, TransactionStatus.COMPLETED);
		return new FundSummaryResponse(inflow, outflow, inflow.subtract(outflow));
	}

	@Transactional
	public FinancialTransactionResponse recordManual(ManualTransactionRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		validateFundAccount(schoolId, request.getFundAccountId());

		TransactionDirection direction = request.getDirection() == ManualTransactionRequest.TransactionDirectionRequest.INFLOW
				? TransactionDirection.INFLOW
				: TransactionDirection.OUTFLOW;

		FinancialTransaction transaction = buildTransaction(
				schoolId,
				direction,
				request.getSourceType(),
				UUID.randomUUID(),
				request.getAmount(),
				request.getPaymentMethod(),
				request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				request.getFundAccountId(),
				request.getNotes());

		if (direction == TransactionDirection.INFLOW) {
			String academicYear = request.getAcademicYear() != null ? request.getAcademicYear() : DEFAULT_ACADEMIC_YEAR;
			transaction.setReceiptNumber(documentNumberGenerator.nextReceiptNumber(
					schoolId, ReceiptSequenceType.RCPT, academicYear));
		}

		return FinancialTransactionResponse.from(transactionRepository.save(transaction));
	}

	@Transactional
	public FinancialTransaction recordInflow(
			SourceType sourceType,
			UUID sourceId,
			BigDecimal amount,
			PaymentMethod paymentMethod,
			String paymentReference,
			LocalDate transactionDate,
			UUID fundAccountId,
			String notes,
			String academicYear) {
		UUID schoolId = schoolContext.getSchoolId();
		validateAmount(amount);
		validateFundAccount(schoolId, fundAccountId);

		FinancialTransaction transaction = buildTransaction(
				schoolId,
				TransactionDirection.INFLOW,
				sourceType,
				sourceId,
				amount,
				paymentMethod,
				paymentReference,
				transactionDate != null ? transactionDate : LocalDate.now(),
				fundAccountId,
				notes);

		String year = academicYear != null ? academicYear : DEFAULT_ACADEMIC_YEAR;
		transaction.setReceiptNumber(documentNumberGenerator.nextReceiptNumber(
				schoolId, ReceiptSequenceType.RCPT, year));

		return transactionRepository.save(transaction);
	}

	@Transactional
	public FinancialTransaction recordOutflow(
			SourceType sourceType,
			UUID sourceId,
			BigDecimal amount,
			PaymentMethod paymentMethod,
			String paymentReference,
			LocalDate transactionDate,
			UUID fundAccountId,
			String notes) {
		UUID schoolId = schoolContext.getSchoolId();
		validateAmount(amount);
		validateFundAccount(schoolId, fundAccountId);

		FinancialTransaction transaction = buildTransaction(
				schoolId,
				TransactionDirection.OUTFLOW,
				sourceType,
				sourceId,
				amount,
				paymentMethod,
				paymentReference,
				transactionDate != null ? transactionDate : LocalDate.now(),
				fundAccountId,
				notes);

		return transactionRepository.save(transaction);
	}

	public BigDecimal getSchoolBalance(UUID schoolId) {
		BigDecimal inflow = transactionRepository.sumBySchoolIdAndDirectionAndStatus(
				schoolId, TransactionDirection.INFLOW, TransactionStatus.COMPLETED);
		BigDecimal outflow = transactionRepository.sumBySchoolIdAndDirectionAndStatus(
				schoolId, TransactionDirection.OUTFLOW, TransactionStatus.COMPLETED);
		return inflow.subtract(outflow);
	}

	private FinancialTransaction buildTransaction(
			UUID schoolId,
			TransactionDirection direction,
			SourceType sourceType,
			UUID sourceId,
			BigDecimal amount,
			PaymentMethod paymentMethod,
			String paymentReference,
			LocalDate transactionDate,
			UUID fundAccountId,
			String notes) {
		FinancialTransaction transaction = new FinancialTransaction();
		transaction.setSchoolId(schoolId);
		transaction.setDirection(direction);
		transaction.setSourceType(sourceType);
		transaction.setSourceId(sourceId);
		transaction.setAmount(amount);
		transaction.setPaymentMethod(paymentMethod);
		transaction.setPaymentReference(paymentReference);
		transaction.setTransactionDate(transactionDate);
		transaction.setStatus(TransactionStatus.COMPLETED);
		transaction.setFundAccountId(fundAccountId);
		transaction.setNotes(notes);
		return transaction;
	}

	private void validateAmount(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Amount must be greater than zero");
		}
	}

	private void validateFundAccount(UUID schoolId, UUID fundAccountId) {
		if (fundAccountId == null) {
			return;
		}
		fundAccountRepository.findByIdAndSchoolId(fundAccountId, schoolId)
				.orElseThrow(() -> new IllegalArgumentException("Fund account not found"));
	}

}
