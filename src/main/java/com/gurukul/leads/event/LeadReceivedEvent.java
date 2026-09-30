package com.gurukul.leads.event;

import java.util.UUID;

/** Published when a lead is saved; the email notification goes out after the save commits. */
public record LeadReceivedEvent(UUID leadId) {
}
