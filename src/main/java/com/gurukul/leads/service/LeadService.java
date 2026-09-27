package com.gurukul.leads.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.leads.dto.LeadDtos.CreateLeadRequest;
import com.gurukul.leads.dto.LeadDtos.LeadResponse;
import com.gurukul.leads.entity.DemoLead;
import com.gurukul.leads.repository.DemoLeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Demo requests from the public marketing site. The POST is unauthenticated by design, so the only
 * defences are here: a honeypot, a per-address rate limit (on a salted hash of the IP, never the raw
 * IP), and bean-validation length caps. Nothing here reads or touches any school's data.
 */
@Service
public class LeadService {

	private static final Logger log = LoggerFactory.getLogger(LeadService.class);

	private static final int LIST_LIMIT = 500;
	private static final Duration WINDOW = Duration.ofHours(1);

	private final DemoLeadRepository demoLeadRepository;
	private final String adminToken;
	private final String ipSalt;
	private final int maxPerWindow;

	public LeadService(
			DemoLeadRepository demoLeadRepository,
			@Value("${app.leads.admin-token:}") String adminToken,
			@Value("${app.leads.ip-hash-salt}") String ipSalt,
			@Value("${app.leads.max-per-ip-per-hour:5}") int maxPerWindow) {
		this.demoLeadRepository = demoLeadRepository;
		this.adminToken = adminToken == null ? "" : adminToken.trim();
		this.ipSalt = ipSalt;
		this.maxPerWindow = maxPerWindow;
	}

	/** Stores the lead unless it is a honeypot hit (silently dropped) or the address is over its limit. */
	@Transactional
	public void submit(CreateLeadRequest request, String clientIp) {
		if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
			// Report success so the bot learns nothing; just don't keep it.
			log.info("Demo lead dropped by honeypot");
			return;
		}
		String ipHash = hashIp(clientIp);
		if (demoLeadRepository.countByIpHashAndCreatedAtAfter(ipHash, Instant.now().minus(WINDOW)) >= maxPerWindow) {
			throw new LeadRateLimitedException();
		}
		DemoLead lead = new DemoLead();
		lead.setName(clean(request.getName()));
		lead.setSchoolName(clean(request.getSchoolName()));
		lead.setRole(clean(request.getRole()));
		lead.setPhone(clean(request.getPhone()));
		lead.setEmail(clean(request.getEmail()));
		lead.setCity(clean(request.getCity()));
		lead.setState(clean(request.getState()));
		lead.setStudentCount(clean(request.getStudentCount()));
		lead.setMessage(clean(request.getMessage()));
		lead.setSourcePage(clean(request.getSourcePage()));
		lead.setIpHash(ipHash);
		DemoLead saved = demoLeadRepository.save(lead);
		// Id only - never names, phones or emails in logs.
		log.info("Demo lead received: {}", saved.getId());
	}

	/**
	 * Newest-first list for the founders / Sales Desk agent. Disabled (404, as if the route didn't
	 * exist) unless LEADS_ADMIN_TOKEN is configured; otherwise requires it as a bearer token.
	 */
	@Transactional(readOnly = true)
	public List<LeadResponse> list(String authorizationHeader) {
		if (adminToken.isEmpty()) {
			throw new EntityNotFoundException("Not found");
		}
		String presented = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
				? authorizationHeader.substring("Bearer ".length()).trim()
				: "";
		if (!MessageDigest.isEqual(
				presented.getBytes(StandardCharsets.UTF_8), adminToken.getBytes(StandardCharsets.UTF_8))) {
			throw new BadCredentialsException("Invalid leads admin token");
		}
		return demoLeadRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, LIST_LIMIT)).stream()
				.map(l -> new LeadResponse(l.getId(), l.getName(), l.getSchoolName(), l.getRole(), l.getPhone(),
						l.getEmail(), l.getCity(), l.getState(), l.getStudentCount(), l.getMessage(),
						l.getSourcePage(), l.getCreatedAt()))
				.toList();
	}

	private String hashIp(String ip) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((ipSalt + "|" + (ip == null ? "" : ip)).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
