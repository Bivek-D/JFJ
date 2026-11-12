package com.jfj_backend.JFJ.util;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/** Age is always worked out from the date of birth and never stored, so it can't go stale. */
public final class AgeUtils {

	public static final int MIN_AGE = 18;

	/** "Today" means today in India, not on the server's clock. */
	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

	private AgeUtils() {
	}

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
