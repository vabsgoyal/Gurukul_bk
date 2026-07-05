package com.gurukul.expenses.infrastructure.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.expenses.infrastructure.dto.InfraExpenseDtos;
import com.gurukul.expenses.infrastructure.entity.*;
import com.gurukul.expenses.infrastructure.repository.*;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.vendors.service.VendorService;
import com.gurukul.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InfraExpenseService {

	public static final String ENTITY_TYPE = "INFRA_EXPENSE";

	private final InfraExpenseCategoryRepository categoryRepository;
	private final InfraExpenseRequestRepository requestRepository;
	private final InfraPurchaseRecordRepository purchaseRepository;
	private final InfraVendorPaymentRepository vendorPaymentRepository;
	private final VendorService vendorService;
	private final WorkflowService workflowService;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<InfraExpenseDtos.CategoryResponse> listCategories() {
		return categoryRepository.findAllBySchoolIdOrderByCodeAsc(schoolContext.getSchoolId()).stream()
				.map(InfraExpenseDtos.CategoryResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<InfraExpenseDtos.RequestResponse> listRequests() {
		return requestRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.map(InfraExpenseDtos.RequestResponse::from).toList();
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse createRequest(InfraExpenseDtos.RequestCreate request) {
		InfraExpenseCategory category = categoryRepository.findById(request.getCategoryId())
				.filter(c -> c.getSchoolId().equals(schoolContext.getSchoolId()))
				.orElseThrow(() -> new EntityNotFoundException("Category not found"));

		InfraExpenseRequest entity = new InfraExpenseRequest();
		entity.setSchoolId(schoolContext.getSchoolId());
		entity.setCategory(category);
		entity.setDescription(request.getDescription());
		entity.setEstimatedAmount(request.getEstimatedAmount());
		entity.setStatus(InfraExpenseStatus.DRAFT);
		entity = requestRepository.save(entity);
		workflowService.getOrCreate(ENTITY_TYPE, entity.getId());
		return InfraExpenseDtos.RequestResponse.from(entity);
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse submit(UUID id, InfraExpenseDtos.ApprovalActionRequest action) {
		InfraExpenseRequest entity = findScoped(id);
		workflowService.submit(ENTITY_TYPE, id, action.getActor() != null ? action.getActor() : "admin");
		entity.setStatus(InfraExpenseStatus.SUBMITTED);
		return InfraExpenseDtos.RequestResponse.from(requestRepository.save(entity));
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse approve(UUID id, InfraExpenseDtos.ApprovalActionRequest action) {
		InfraExpenseRequest entity = findScoped(id);
		workflowService.approve(ENTITY_TYPE, id, action.getActor() != null ? action.getActor() : "principal", action.getComment());
		entity.setStatus(InfraExpenseStatus.APPROVED);
		return InfraExpenseDtos.RequestResponse.from(requestRepository.save(entity));
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse reject(UUID id, InfraExpenseDtos.ApprovalActionRequest action) {
		InfraExpenseRequest entity = findScoped(id);
		workflowService.reject(ENTITY_TYPE, id, action.getActor() != null ? action.getActor() : "principal", action.getComment());
		entity.setStatus(InfraExpenseStatus.REJECTED);
		return InfraExpenseDtos.RequestResponse.from(requestRepository.save(entity));
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse recordPurchase(UUID id, InfraExpenseDtos.PurchaseRequest request) {
		InfraExpenseRequest entity = findScoped(id);
		if (entity.getStatus() != InfraExpenseStatus.APPROVED) {
			throw new IllegalArgumentException("Only approved requests can be purchased");
		}
		if (purchaseRepository.findByRequestId(id).isPresent()) {
			throw new IllegalArgumentException("Purchase already recorded");
		}
		InfraPurchaseRecord purchase = new InfraPurchaseRecord();
		purchase.setSchoolId(schoolContext.getSchoolId());
		purchase.setRequest(entity);
		purchase.setVendor(vendorService.getScopedEntity(request.getVendorId()));
		purchase.setInvoiceNumber(request.getInvoiceNumber());
		purchase.setActualAmount(request.getActualAmount());
		purchaseRepository.save(purchase);
		entity.setStatus(InfraExpenseStatus.PURCHASED);
		return InfraExpenseDtos.RequestResponse.from(requestRepository.save(entity));
	}

	@Transactional
	public InfraExpenseDtos.RequestResponse payVendor(UUID id, InfraExpenseDtos.PayRequest request) {
		InfraExpenseRequest entity = findScoped(id);
		if (entity.getStatus() != InfraExpenseStatus.PURCHASED) {
			throw new IllegalArgumentException("Only purchased requests can be paid");
		}
		InfraPurchaseRecord purchase = purchaseRepository.findByRequestId(id)
				.orElseThrow(() -> new EntityNotFoundException("Purchase record not found"));
		if (vendorPaymentRepository.findByPurchaseId(purchase.getId()).isPresent()) {
			throw new IllegalArgumentException("Vendor already paid");
		}

		FinancialTransaction tx = ledgerService.recordOutflow(
				SourceType.VENDOR_PAYMENT, purchase.getId(), purchase.getActualAmount(),
				request.getPaymentMethod(), request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				null, "Infra expense: " + entity.getDescription());

		InfraVendorPayment payment = new InfraVendorPayment();
		payment.setSchoolId(schoolContext.getSchoolId());
		payment.setPurchase(purchase);
		payment.setTransactionId(tx.getId());
		vendorPaymentRepository.save(payment);

		entity.setStatus(InfraExpenseStatus.PAID);
		return InfraExpenseDtos.RequestResponse.from(requestRepository.save(entity));
	}

	private InfraExpenseRequest findScoped(UUID id) {
		return requestRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Expense request not found"));
	}

}
