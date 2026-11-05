package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jfj_backend.JFJ.util.AgeUtils;

// Plain unit test: no Spring, no database, runs in milliseconds.
class AgeUtilsTests {

	private final LocalDate today = LocalDate.of(2026, 11, 5);

	@Test
	void eighteenthBirthdayTodayIsAdult() {
		assertThat(AgeUtils.isAdultOn(LocalDate.of(2008, 11, 5), today)).isTrue();
	}

	@Test
	void eighteenthBirthdayTomorrowIsNotAdult() {
		assertThat(AgeUtils.isAdultOn(LocalDate.of(2008, 11, 6), today)).isFalse();
	}

	@Test
	void leapDayBirthdayCountsFromFirstMarchInNonLeapYears() {
		// Born 29 Feb 2008; 2026 has no 29 Feb, so the 18th birthday falls on 1 March.
		assertThat(AgeUtils.isAdultOn(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28))).isFalse();
		assertThat(AgeUtils.isAdultOn(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 3, 1))).isTrue();
	}
}
