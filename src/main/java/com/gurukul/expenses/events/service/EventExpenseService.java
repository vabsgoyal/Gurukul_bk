package com.gurukul.expenses.events.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.collections.service.EventCollectionService;
import com.gurukul.events.entity.SchoolEvent;
import com.gurukul.events.service.EventService;
import com.gurukul.expenses.events.dto.EventExpenseDtos;
import com.gurukul.expenses.events.entity.*;
import com.gurukul.expenses.events.repository.*;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.vendors.service.VendorService;
import com.gurukul.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventExpenseService {

	public static final String ENTITY_TYPE = "EVENT_EXPENSE";

	private final EventBudgetRepository budgetRepository;
	private final EventBudgetLineRepository budgetLineRepository;
	private final EventExpenseRequestRepository expenseRequestRepository;
	private final EventVendorPaymentRepository vendorPaymentRepository;
	private final EventService eventService;
	private final EventCollectionService eventCollectionService;
	private final VendorService vendorService;
	private final WorkflowService workflowService;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;

	@Transactional
	public EventExpenseDtos.BudgetResponse createBudget(UUID eventId, EventExpenseDtos.BudgetRequest request) {
		SchoolEvent event = requireOutflowEnabled(eventId);
		if (budgetRepository.findByEventId(eventId).isPresent()) {
			throw new IllegalArgumentException("Budget already exists for this event");
		}
		EventBudget budget = new EventBudget();
		budget.setSchoolId(schoolContext.getSchoolId());
		budget.setEvent(event);
		budget = budgetRepository.save(budget);

		for (EventExpenseDtos.BudgetLineRequest lineReq : request.getLines()) {
			EventBudgetLine line = new EventBudgetLine();
			line.setSchoolId(schoolContext.getSchoolId());
			line.setBudget(budget);
			line.setDescription(lineReq.getDescription());
			line.setPlannedAmount(lineReq.getPlannedAmount());
			budgetLineRepository.save(line);
		}
		return getBudget(eventId);
	}

	@Transactional(readOnly = true)
	public EventExpenseDtos.BudgetResponse getBudget(UUID eventId) {
		EventBudget budget = budgetRepository.findByEventId(eventId)
				.orElseThrow(() -> new EntityNotFoundException("Event budget not found"));
		List<EventBudgetLine> lines = budgetLineRepository.findAllByBudgetId(budget.getId());
		return EventExpenseDtos.BudgetResponse.from(budget, lines);
	}

	@Transactional
	public EventExpenseDtos.ExpenseRequestResponse createExpenseRequest(UUID eventId, EventExpenseDtos.ExpenseRequestCreate request) {
		requireOutflowEnabled(eventId);
		EventBudgetLine line = budgetLineRepository.findById(request.getBudgetLineId())
				.filter(l -> l.getSchoolId().equals(schoolContext.getSchoolId()))
				.orElseThrow(() -> new EntityNotFoundException("Budget line not found"));

		EventExpenseRequest entity = new EventExpenseRequest();
		entity.setSchoolId(schoolContext.getSchoolId());
		entity.setBudgetLine(line);
		entity.setDescription(request.getDescription());
		entity.setEstimatedAmount(request.getEstimatedAmount());
		entity.setStatus(EventExpenseStatus.DRAFT);
		entity = expenseRequestRepository.save(entity);
		workflowService.getOrCreate(ENTITY_TYPE, entity.getId());
		return EventExpenseDtos.ExpenseRequestResponse.from(entity);
	}

	@Transactional
	public EventExpenseDtos.ExpenseRequestResponse submit(UUID eventId, UUID reqId, EventExpenseDtos.ApprovalActionRequest action) {
		EventExpenseRequest entity = findExpenseRequest(eventId, reqId);
		workflowService.submit(ENTITY_TYPE, reqId, action.getActor() != null ? action.getActor() : "admin");
		entity.setStatus(EventExpenseStatus.SUBMITTED);
		return EventExpenseDtos.ExpenseRequestResponse.from(expenseRequestRepository.save(entity));
	}

	@Transactional
	public EventExpenseDtos.ExpenseRequestResponse approve(UUID eventId, UUID reqId, EventExpenseDtos.ApprovalActionRequest action) {
		EventExpenseRequest entity = findExpenseRequest(eventId, reqId);
		workflowService.approve(ENTITY_TYPE, reqId, action.getActor() != null ? action.getActor() : "principal", action.getComment());
		entity.setStatus(EventExpenseStatus.APPROVED);
		return EventExpenseDtos.ExpenseRequestResponse.from(expenseRequestRepository.save(entity));
	}

	@Transactional
	public EventExpenseDtos.ExpenseRequestResponse pay(UUID eventId, UUID reqId, EventExpenseDtos.PayRequest request) {
		EventExpenseRequest entity = findExpenseRequest(eventId, reqId);
		if (entity.getStatus() != EventExpenseStatus.APPROVED) {
			throw new IllegalArgumentException("Only approved expense requests can be paid");
		}

		FinancialTransaction tx = ledgerService.recordOutflow(
				SourceType.VENDOR_PAYMENT, entity.getId(), entity.getEstimatedAmount(),
				request.getPaymentMethod(), request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				null, "Event expense: " + entity.getDescription());

		EventVendorPayment payment = new EventVendorPayment();
		payment.setSchoolId(schoolContext.getSchoolId());
		payment.setRequest(entity);
		payment.setVendor(vendorService.getScopedEntity(request.getVendorId()));
		payment.setTransactionId(tx.getId());
		vendorPaymentRepository.save(payment);

		entity.setStatus(EventExpenseStatus.PAID);
		return EventExpenseDtos.ExpenseRequestResponse.from(expenseRequestRepository.save(entity));
	}

	@Transactional(readOnly = true)
	public EventExpenseDtos.EventPnlResponse getPnl(UUID eventId) {
		eventService.getScopedEntity(eventId);
		BigDecimal collections = eventCollectionService.getTotalCollections(eventId);
		BigDecimal expenses = vendorPaymentRepository.sumPaidExpensesByEventId(eventId);

		BigDecimal planned = budgetRepository.findByEventId(eventId)
				.map(b -> budgetLineRepository.findAllByBudgetId(b.getId()).stream()
						.map(EventBudgetLine::getPlannedAmount)
						.reduce(BigDecimal.ZERO, BigDecimal::add))
				.orElse(BigDecimal.ZERO);

		return new EventExpenseDtos.EventPnlResponse(
				eventId, collections, expenses, collections.subtract(expenses), planned, expenses);
	}

	public BigDecimal getTotalExpenses(UUID eventId) {
		return vendorPaymentRepository.sumPaidExpensesByEventId(eventId);
	}

	private EventExpenseRequest findExpenseRequest(UUID eventId, UUID reqId) {
		EventExpenseRequest entity = expenseRequestRepository.findByIdAndSchoolId(reqId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Expense request not found"));
		if (!entity.getBudgetLine().getBudget().getEvent().getId().equals(eventId)) {
			throw new EntityNotFoundException("Expense request not found for this event");
		}
		return entity;
	}

	private SchoolEvent requireOutflowEnabled(UUID eventId) {
		SchoolEvent event = eventService.getScopedEntity(eventId);
		if (!event.isOutflowEnabled()) {
			throw new IllegalArgumentException("Outflow is not enabled for this event");
		}
		return event;
	}

}
