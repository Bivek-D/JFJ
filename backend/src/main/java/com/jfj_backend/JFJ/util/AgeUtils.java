package com.jfj_backend.JFJ.util;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/** Age rules (AUTH-2). Age is always worked out from the date of birth and never stored. */
public final class AgeUtils {

	public static final int MIN_AGE = 18;

	/** Business timezone (PRD section 10): "today" means today in India, not on the server's clock. */
	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

	private AgeUtils() {
	}

	/** Full years between birth and the given day. Someone born 2008-11-05 is 18 on 2026-11-05. */
	public static int ageOn(LocalDate dateOfBirth, LocalDate day) {
		return Period.between(dateOfBirth, day).getYears();
	}

	public static boolean isAdultOn(LocalDate dateOfBirth, LocalDate day) {
		return ageOn(dateOfBirth, day) >= MIN_AGE;
	}

	public static LocalDate todayInIndia() {
		return LocalDate.now(BUSINESS_ZONE);
	}
}
