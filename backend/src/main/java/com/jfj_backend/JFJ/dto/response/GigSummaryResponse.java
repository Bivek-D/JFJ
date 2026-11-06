package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

/**
 * One card in the gig list (GET /gigs). lat/lng are the APPROXIMATE point; there is no exact point or
 * address line here. distanceKm is rounded to 0.5 km (FIND-5) and is null when no location was sent.
 * Not yet: openings left (B4), provider rating and level (B5).
 */
public record GigSummaryResponse(UUID id, String title, CategoryResponse category, PayType payType,
		BigDecimal payAmount, int workersNeeded, ExperienceLevel minExperience, LocalDate startDate,
		String durationText, String locality, String city, double lat, double lng, Double distanceKm,
		Instant createdAt) {
}
