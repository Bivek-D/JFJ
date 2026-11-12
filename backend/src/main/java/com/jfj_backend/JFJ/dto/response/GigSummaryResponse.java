package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

/** lat/lng are the approximate point; there is deliberately no exact point or address line here. */
public record GigSummaryResponse(UUID id, String title, CategoryResponse category, PayType payType,
		BigDecimal payAmount, int workersNeeded, ExperienceLevel minExperience, LocalDate startDate,
		String durationText, String locality, String city, double lat, double lng, Double distanceKm,
		Instant createdAt) {
}
