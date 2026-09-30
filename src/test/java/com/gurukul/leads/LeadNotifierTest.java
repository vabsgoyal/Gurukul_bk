package com.gurukul.leads;

import com.gurukul.leads.entity.DemoLead;
import com.gurukul.leads.entity.LeadType;
import com.gurukul.leads.event.LeadReceivedEvent;
import com.gurukul.leads.repository.DemoLeadRepository;
import com.gurukul.leads.service.LeadNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The new-lead email: who it goes to, what it says, and that it can never fail a submission. */
class LeadNotifierTest {

	private final DemoLeadRepository repository = mock(DemoLeadRepository.class);
	private final JavaMailSender sender = mock(JavaMailSender.class);

	@SuppressWarnings("unchecked")
	private LeadNotifier notifier(String notifyEmail, JavaMailSender mailSender) {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(mailSender);
		return new LeadNotifier(repository, provider, notifyEmail, "Smart Gurukul <no-reply@smartgurukul.org>");
	}

	private DemoLead websiteLead() {
		DemoLead lead = new DemoLead();
		lead.setId(UUID.randomUUID());
		lead.setName("Ravi Jain");
		lead.setSchoolName("Green Valley\r\nBcc: attacker@example.org");
		lead.setPhone("9876543210");
		lead.setEmail("ravi@example.org");
		lead.setRequestType(LeadType.WEBSITE_SERVICES);
		lead.setServices("New school website, Online admission form");
		lead.setBudget("15k-30k");
		lead.setMessage("Need it before admissions open.");
		lead.setCreatedAt(Instant.parse("2026-09-30T06:30:00Z"));
		when(repository.findById(lead.getId())).thenReturn(Optional.of(lead));
		return lead;
	}

	@Test
	void emailsEveryRecipientWithTheLeadAndReplyToTheProspect() {
		DemoLead lead = websiteLead();
		notifier(" founder@example.org, sales@example.org ,", sender).onLeadReceived(new LeadReceivedEvent(lead.getId()));

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(sender).send(sent.capture());
		SimpleMailMessage mail = sent.getValue();
		assertThat(mail.getTo()).containsExactly("founder@example.org", "sales@example.org");
		assertThat(mail.getReplyTo()).isEqualTo("ravi@example.org");
		assertThat(mail.getFrom()).isEqualTo("Smart Gurukul <no-reply@smartgurukul.org>");
		assertThat(mail.getSubject()).startsWith("Website services request: Green Valley Bcc:")
				.doesNotContain("\n").doesNotContain("\r");
		assertThat(mail.getText())
				.contains("Phone: 9876543210")
				.contains("Services: New school website, Online admission form")
				.contains("Budget: 15k-30k")
				.contains("Received: 30 Sep 2026, 12:00 PM IST")
				.contains("Need it before admissions open.")
				.doesNotContain("City:");
	}

	@Test
	void doesNothingUntilRecipientsAndSmtpAreConfigured() {
		DemoLead lead = websiteLead();
		notifier("", sender).onLeadReceived(new LeadReceivedEvent(lead.getId()));
		verifyNoInteractions(sender);

		notifier("founder@example.org", null).onLeadReceived(new LeadReceivedEvent(lead.getId()));
		verify(repository, never()).findById(any());
	}

	@Test
	void smtpFailureIsSwallowed() {
		DemoLead lead = websiteLead();
		doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
		assertThatCode(() -> notifier("founder@example.org", sender).onLeadReceived(new LeadReceivedEvent(lead.getId())))
				.doesNotThrowAnyException();
	}

}
