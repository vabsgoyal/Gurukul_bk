package com.gurukul.admissions.entity;

import java.util.Map;
import java.util.Set;

/**
 * NEW -> UNDER_REVIEW -> APPROVED / REJECTED -> ENROLLED. ENROLLED is only reachable through
 * enrolment (AdmissionService.convert), never a plain stage change, and is terminal.
 */
public enum AdmissionStage {
	NEW,
	UNDER_REVIEW,
	APPROVED,
	REJECTED,
	ENROLLED;

	private static final Map<AdmissionStage, Set<AdmissionStage>> ALLOWED = Map.of(
			NEW, Set.of(UNDER_REVIEW, REJECTED),
			UNDER_REVIEW, Set.of(APPROVED, REJECTED),
			// Undo an approval, or reopen a rejection - both go back to review rather than jumping.
			APPROVED, Set.of(UNDER_REVIEW),
			REJECTED, Set.of(UNDER_REVIEW),
			ENROLLED, Set.of());

	public boolean canMoveTo(AdmissionStage target) {
		return ALLOWED.get(this).contains(target);
	}
}
