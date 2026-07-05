package com.gurukul.sponsorships.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.finance.entity.FinancialTransaction;
import com.gurukul.finance.entity.SourceType;
import com.gurukul.finance.service.LedgerService;
import com.gurukul.sponsorships.dto.SponsorshipDtos;
import com.gurukul.sponsorships.entity.Sponsor;
import com.gurukul.sponsorships.entity.Sponsorship;
import com.gurukul.sponsorships.entity.SponsorshipPayment;
import com.gurukul.sponsorships.entity.SponsorshipPurpose;
import com.gurukul.sponsorships.entity.SponsorshipStatus;
import com.gurukul.sponsorships.repository.SponsorRepository;
import com.gurukul.sponsorships.repository.SponsorshipPaymentRepository;
import com.gurukul.sponsorships.repository.SponsorshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SponsorshipService {

	private final SponsorRepository sponsorRepository;
	private final SponsorshipRepository sponsorshipRepository;
	private final SponsorshipPaymentRepository sponsorshipPaymentRepository;
	private final LedgerService ledgerService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<SponsorshipDtos.SponsorResponse> listSponsors() {
		return sponsorRepository.findAllBySchoolIdOrderByNameAsc(schoolContext.getSchoolId()).stream()
				.map(SponsorshipDtos.SponsorResponse::from).toList();
	}

	@Transactional
	public SponsorshipDtos.SponsorResponse createSponsor(SponsorshipDtos.SponsorRequest request) {
		Sponsor sponsor = new Sponsor();
		sponsor.setSchoolId(schoolContext.getSchoolId());
		sponsor.setName(request.getName());
		sponsor.setContactPhone(request.getContactPhone());
		sponsor.setContactEmail(request.getContactEmail());
		sponsor.setPan(request.getPan());
		return SponsorshipDtos.SponsorResponse.from(sponsorRepository.save(sponsor));
	}

	@Transactional(readOnly = true)
	public List<SponsorshipDtos.SponsorshipResponse> listSponsorships(SponsorshipPurpose purpose) {
		UUID schoolId = schoolContext.getSchoolId();
		List<Sponsorship> list = purpose != null
				? sponsorshipRepository.findAllBySchoolIdAndPurpose(schoolId, purpose)
				: sponsorshipRepository.findAllBySchoolId(schoolId);
		return list.stream().map(s -> SponsorshipDtos.SponsorshipResponse.from(s, receivedAmount(s.getId()))).toList();
	}

	@Transactional
	public SponsorshipDtos.SponsorshipResponse createSponsorship(SponsorshipDtos.SponsorshipRequest request) {
		Sponsor sponsor = sponsorRepository.findByIdAndSchoolId(request.getSponsorId(), schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Sponsor not found"));
		Sponsorship sponsorship = new Sponsorship();
		sponsorship.setSchoolId(schoolContext.getSchoolId());
		sponsorship.setSponsor(sponsor);
		sponsorship.setPurpose(request.getPurpose());
		sponsorship.setPledgedAmount(request.getPledgedAmount());
		sponsorship.setFundAccountId(request.getFundAccountId());
		sponsorship.setStatus(SponsorshipStatus.PLEDGED);
		sponsorship = sponsorshipRepository.save(sponsorship);
		return SponsorshipDtos.SponsorshipResponse.from(sponsorship, BigDecimal.ZERO);
	}

	@Transactional
	public SponsorshipDtos.SponsorshipPaymentResponse recordPayment(UUID id, SponsorshipDtos.SponsorshipPaymentRequest request) {
		Sponsorship sponsorship = sponsorshipRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Sponsorship not found"));
		BigDecimal received = receivedAmount(id);
		BigDecimal remaining = sponsorship.getPledgedAmount().subtract(received);
		if (request.getAmount().compareTo(remaining) > 0) {
			throw new IllegalArgumentException("Payment exceeds remaining pledged amount");
		}

		SponsorshipPayment payment = new SponsorshipPayment();
		payment.setSchoolId(schoolContext.getSchoolId());
		payment.setSponsorship(sponsorship);
		payment.setAmount(request.getAmount());

		FinancialTransaction tx = ledgerService.recordInflow(
				SourceType.SPONSORSHIP, id, request.getAmount(), request.getPaymentMethod(),
				request.getPaymentReference(),
				request.getTransactionDate() != null ? request.getTransactionDate() : LocalDate.now(),
				sponsorship.getFundAccountId(), "Sponsorship: " + sponsorship.getPurpose(), "2026-27");

		payment.setTransactionId(tx.getId());
		payment = sponsorshipPaymentRepository.save(payment);

		BigDecimal newReceived = received.add(request.getAmount());
		sponsorship.setStatus(computeStatus(sponsorship.getPledgedAmount(), newReceived));
		sponsorshipRepository.save(sponsorship);

		return SponsorshipDtos.SponsorshipPaymentResponse.from(payment, tx.getReceiptNumber());
	}

	private BigDecimal receivedAmount(UUID sponsorshipId) {
		return sponsorshipPaymentRepository.sumBySponsorshipId(sponsorshipId);
	}

	static SponsorshipStatus computeStatus(BigDecimal pledged, BigDecimal received) {
		if (received.compareTo(pledged) >= 0) return SponsorshipStatus.RECEIVED;
		if (received.compareTo(BigDecimal.ZERO) > 0) return SponsorshipStatus.PARTIAL;
		return SponsorshipStatus.PLEDGED;
	}

}
