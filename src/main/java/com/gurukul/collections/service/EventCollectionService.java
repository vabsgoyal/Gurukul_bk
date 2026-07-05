package com.gurukul.collections.service;

import com.gurukul.common.SchoolContext;
import com.gurukul.collections.dto.CollectionDtos;
import com.gurukul.collections.entity.EventCollectionPayment;
import com.gurukul.collections.entity.EventParticipationFee;
import com.gurukul.collections.repository.EventCollectionPaymentRepository;
import com.gurukul.collections.repository.EventParticipationFeeRepository;
import com.gurukul.events.entity.SchoolEvent;
import com.gurukul.events.service.EventService;
import com.gurukul.expenses.events.repository.EventVendorPaymentRepository;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventCollectionService {

	private final EventParticipationFeeRepository participationFeeRepository;
	private final EventCollectionPaymentRepository collectionPaymentRepository;
	private final EventVendorPaymentRepository eventVendorPaymentRepository;
	private final EventService eventService;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<CollectionDtos.ParticipationFeeResponse> listParticipationFees(UUID eventId) {
		requireInflowEnabled(eventId);
		return participationFeeRepository.findAllByEventId(eventId).stream()
				.map(CollectionDtos.ParticipationFeeResponse::from)
				.toList();
	}

	@Transactional
	public CollectionDtos.ParticipationFeeResponse addParticipationFee(
			UUID eventId, CollectionDtos.ParticipationFeeRequest request) {
		SchoolEvent event = requireInflowEnabled(eventId);
		if (participationFeeRepository.findByEventIdAndParticipantType(eventId, request.getParticipantType()).isPresent()) {
			throw new IllegalArgumentException("Participation fee already exists for this type");
		}
		EventParticipationFee fee = new EventParticipationFee();
		fee.setSchoolId(schoolContext.getSchoolId());
		fee.setEvent(event);
		fee.setParticipantType(request.getParticipantType());
		fee.setAmount(request.getAmount());
		return CollectionDtos.ParticipationFeeResponse.from(participationFeeRepository.save(fee));
	}

	@Transactional(readOnly = true)
	public List<CollectionDtos.CollectionPaymentResponse> listCollections(UUID eventId) {
		requireInflowEnabled(eventId);
		return collectionPaymentRepository.findAllByEventId(eventId).stream()
				.map(p -> CollectionDtos.CollectionPaymentResponse.from(p, null))
				.toList();
	}

	@Transactional
	public CollectionDtos.CollectionPaymentResponse recordCollection(
			UUID eventId, CollectionDtos.CollectionPaymentRequest request) {
		SchoolEvent event = requireInflowEnabled(eventId);
		EventCollectionPayment payment = new EventCollectionPayment();
		payment.setSchoolId(schoolContext.getSchoolId());
		payment.setEvent(event);
		payment.setPayerName(request.getPayerName());
		payment.setPayerReference(request.getPayerReference());
		payment.setAmount(request.getAmount());

		FinancialTransaction transaction = ledgerService.recordInflow(
				SourceType.EVENT_COLLECTION,
				eventId,
				request.getAmount(),
				request.getPaymentMethod(),
				request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				null,
				"Event collection: " + event.getName(),
				"2026-27");

		payment.setTransactionId(transaction.getId());
		payment = collectionPaymentRepository.save(payment);
		return CollectionDtos.CollectionPaymentResponse.from(payment, transaction.getReceiptNumber());
	}

	@Transactional(readOnly = true)
	public CollectionDtos.EventBalanceResponse getBalance(UUID eventId) {
		eventService.getScopedEntity(eventId);
		BigDecimal collections = collectionPaymentRepository.sumByEventId(eventId);
		BigDecimal expenses = eventVendorPaymentRepository.sumPaidExpensesByEventId(eventId);
		return new CollectionDtos.EventBalanceResponse(eventId, collections, expenses, collections.subtract(expenses));
	}

	public BigDecimal getTotalCollections(UUID eventId) {
		return collectionPaymentRepository.sumByEventId(eventId);
	}

	private SchoolEvent requireInflowEnabled(UUID eventId) {
		SchoolEvent event = eventService.getScopedEntity(eventId);
		if (!event.isInflowEnabled()) {
			throw new IllegalArgumentException("Inflow is not enabled for this event");
		}
		return event;
	}

}
