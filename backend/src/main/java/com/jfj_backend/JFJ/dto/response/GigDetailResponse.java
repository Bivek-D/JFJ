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
 * GET /api/v1/gigs/{id} (FIND-3, GIG-5). lat/lng are ALWAYS the approximate point.
 * exact is null unless the viewer may see it: the owner, or a worker with an ACTIVE assignment on it.
 * The frontend draws a 500 m circle when exact is null and a pin when it isn't.
 * openingsLeft = workersNeeded - ACTIVE assignments. Not yet: provider rating and level (B5), photos.
 */
public record GigDetailResponse(UUID id, String title, String description, CategoryResponse category,
		PayType payType, BigDecimal payAmount, int workersNeeded, int openingsLeft, ExperienceLevel minExperience,
		LocalDate startDate, String durationText, LocalDate applyDeadline, List<SkillResponse> skills,
		String locality, String city, double lat, double lng, GigStatus status, ProviderSummary provider,
		boolean mine, ExactLocation exact, Instant createdAt) {

	public record ProviderSummary(UUID id, String name) {
	}

	/** Only for the owner and hired workers: exact pin, full address, provider phone. */
	public record ExactLocation(double lat, double lng, String addressLine, String providerPhone) {
	}

	public static GigDetailResponse from(Gig gig, int openingsLeft, boolean mine, boolean showExact) {
		List<SkillResponse> skills = gig.getSkills().stream()
				.map(SkillResponse::from)
				.sorted(Comparator.comparing(SkillResponse::name))
				.toList();
		ExactLocation exact = showExact
				? new ExactLocation(GeoUtils.lat(gig.getLocation()), GeoUtils.lng(gig.getLocation()),
						gig.getAddressLine(), gig.getProvider().getPhone())
				: null;
		return new GigDetailResponse(gig.getId(), gig.getTitle(), gig.getDescription(),
				CategoryResponse.from(gig.getCategory()), gig.getPayType(), gig.getPayAmount(), gig.getWorkersNeeded(),
				openingsLeft, gig.getMinExperience(), gig.getStartDate(), gig.getDurationText(),
				gig.getApplyDeadline(), skills, gig.getLocality(), gig.getCity(), GeoUtils.lat(gig.getPublicLocation()),
				GeoUtils.lng(gig.getPublicLocation()), gig.getStatus(),
				new ProviderSummary(gig.getProvider().getId(), gig.getProvider().getName()), mine, exact,
				gig.getCreatedAt());
	}
}
