package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.enums.PayType;
import com.jfj_backend.JFJ.util.GeoUtils;

/**
 * A gig as its owner sees it: everything, including the exact pin and address, plus the approximate
 * point so the owner can see what workers see. Only returned from provider endpoints on their own gigs.
 * The public, masked version comes on Day 8.
 */
public record OwnerGigResponse(UUID id, String title, String description, CategoryResponse category,
		PayType payType, BigDecimal payAmount, int workersNeeded, ExperienceLevel minExperience,
		LocalDate startDate, String durationText, LocalDate applyDeadline, List<SkillResponse> skills,
		double lat, double lng, double approxLat, double approxLng, String addressLine, String locality,
		String city, int locationEditsLeft, GigStatus status, Instant createdAt) {

	public static OwnerGigResponse from(Gig gig) {
		List<SkillResponse> skills = gig.getSkills().stream()
				.map(SkillResponse::from)
				.sorted(Comparator.comparing(SkillResponse::name))
				.toList();
		return new OwnerGigResponse(gig.getId(), gig.getTitle(), gig.getDescription(),
				CategoryResponse.from(gig.getCategory()), gig.getPayType(), gig.getPayAmount(),
				gig.getWorkersNeeded(), gig.getMinExperience(), gig.getStartDate(), gig.getDurationText(),
				gig.getApplyDeadline(), skills, GeoUtils.lat(gig.getLocation()), GeoUtils.lng(gig.getLocation()),
				GeoUtils.lat(gig.getPublicLocation()), GeoUtils.lng(gig.getPublicLocation()), gig.getAddressLine(),
				gig.getLocality(), gig.getCity(), Gig.MAX_LOCATION_EDITS - gig.getLocationEditCount(),
				gig.getStatus(), gig.getCreatedAt());
	}
}
