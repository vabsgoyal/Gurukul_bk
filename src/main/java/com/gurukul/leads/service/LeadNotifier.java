package com.gurukul.leads.service;

import com.gurukul.leads.entity.DemoLead;
import com.gurukul.leads.entity.LeadType;
import com.gurukul.leads.event.LeadReceivedEvent;
import com.gurukul.leads.repository.DemoLeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Emails the founders each new lead so they don't have to poll GET /api/v1/leads. Runs after the
 * save commits and off the request thread, so a slow or broken SMTP server never delays or fails
 * the visitor's form submission. A no-op until both LEADS_NOTIFY_EMAIL and an SMTP host are set.
 * Never throws - the lead is already stored either way.
 */
@Component
public class LeadNotifier {

	private static final Logger log = LoggerFactory.getLogger(LeadNotifier.class);
	private static final DateTimeFormatter WHEN =
			DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

	private final DemoLeadRepository demoLeadRepository;
	private final ObjectProvider<JavaMailSender> mailSender;
	private final String[] recipients;
	private final String from;

	public LeadNotifier(
			DemoLeadRepository demoLeadRepository,
			ObjectProvider<JavaMailSender> mailSender,
			@Value("${app.leads.notify-email:}") String notifyEmail,
			@Value("${app.leads.mail-from:}") String from) {
		this.demoLeadRepository = demoLeadRepository;
		this.mailSender = mailSender;
		this.recipients = Arrays.stream(notifyEmail == null ? new String[0] : notifyEmail.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toArray(String[]::new);
		this.from = from == null ? "" : from.trim();
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onLeadReceived(LeadReceivedEvent event) {
		JavaMailSender sender = mailSender.getIfAvailable();
		if (recipients.length == 0 || sender == null) {
			return;
		}
		try {
			DemoLead lead = demoLeadRepository.findById(event.leadId()).orElse(null);
			if (lead == null) {
				return;
			}
			sender.send(buildMessage(lead));
			log.info("Lead notification sent for {}", lead.getId());
		} catch (Exception e) {
			// Id only - never the prospect's details in logs.
			log.warn("Lead notification for {} failed", event.leadId(), e);
		}
	}

	SimpleMailMessage buildMessage(DemoLead lead) {
		boolean website = lead.getRequestType() == LeadType.WEBSITE_SERVICES;
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(recipients);
		if (!from.isEmpty()) {
			message.setFrom(from);
		}
		if (lead.getEmail() != null) {
			// Hitting Reply in the mailbox goes straight to the prospect.
			message.setReplyTo(lead.getEmail());
		}
		message.setSubject(oneLine((website ? "Website services request: " : "Demo request: ")
				+ lead.getSchoolName() + " (" + lead.getName() + ")"));

		StringBuilder body = new StringBuilder();
		body.append(website ? "New website services request" : "New demo request")
				.append(" from smartgurukul.org\n\n");
		line(body, "Name", lead.getName());
		line(body, website ? "School / organisation" : "School", lead.getSchoolName());
		line(body, "Role", lead.getRole());
		line(body, "Phone", lead.getPhone());
		line(body, "Email", lead.getEmail());
		line(body, "City", lead.getCity());
		line(body, "State", lead.getState());
		line(body, "Students", lead.getStudentCount());
		line(body, "Services", lead.getServices());
		line(body, "Budget", lead.getBudget());
		line(body, "Page", lead.getSourcePage());
		line(body, "Received", lead.getCreatedAt() == null ? null : WHEN.format(lead.getCreatedAt()));
		if (lead.getMessage() != null) {
			body.append("\nMessage:\n").append(lead.getMessage()).append('\n');
		}
		body.append("\nLead id: ").append(lead.getId()).append('\n');
		message.setText(body.toString());
		return message;
	}

	private static void line(StringBuilder body, String label, String value) {
		if (value != null && !value.isBlank()) {
			body.append(label).append(": ").append(oneLine(value)).append('\n');
		}
	}

	/** Visitor-supplied text must not break out of its header or line. */
	private static String oneLine(String value) {
		return value.replaceAll("[\r\n]+", " ").trim();
	}

}
