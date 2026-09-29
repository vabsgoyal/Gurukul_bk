package com.gurukul.fees.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeeDueAlertWindowTest {

	private static final LocalDate DUE = LocalDate.of(2026, 10, 15);

	@Test
	void nothingEarlierThanThreeDaysBefore() {
		assertThat(FeeDueAlertService.window(DUE, DUE.minusDays(4))).isEmpty();
		assertThat(FeeDueAlertService.window(null, DUE)).isEmpty();
	}

	@Test
	void oneWindowFromThreeDaysBeforeThroughTheDueDate() {
		for (int daysBefore = 0; daysBefore <= 3; daysBefore++) {
			assertThat(FeeDueAlertService.window(DUE, DUE.minusDays(daysBefore))).isEqualTo(Optional.of("PRE"));
		}
	}

	@Test
	void aNewWindowEachWeekWhileOverdue() {
		assertThat(FeeDueAlertService.window(DUE, DUE.plusDays(1))).contains("OVERDUE-0");
		assertThat(FeeDueAlertService.window(DUE, DUE.plusDays(7))).contains("OVERDUE-0");
		assertThat(FeeDueAlertService.window(DUE, DUE.plusDays(8))).contains("OVERDUE-1");
		assertThat(FeeDueAlertService.window(DUE, DUE.plusDays(14))).contains("OVERDUE-1");
		assertThat(FeeDueAlertService.window(DUE, DUE.plusDays(15))).contains("OVERDUE-2");
	}

}
