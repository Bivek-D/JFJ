package com.jfj_backend.JFJ.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of GET /api/v1/gigs (FIND-1, FIND-2). Spring fills this record from the URL
 * (?lat=..&lng=..&radiusKm=..). Everything is optional; lat and lng go together.
 * experience = the worker's own level: gigs that need that level or lower are shown.
 */
public record GigSearchParams(
		@DecimalMin("-90") @DecimalMax("90") Double lat,
		@DecimalMin("-180") @DecimalMax("180") Double lng,
		@Min(1) @Max(50) Integer radiusKm,
		Long categoryId,
		@DecimalMin("0") BigDecimal minPay,
		@DecimalMin("0") BigDecimal maxPay,
		PayType payType,
		ExperienceLevel experience,
		LocalDate startFrom,
		@Size(max = 100) String q,
		@Min(0) Integer page,
		@Min(1) @Max(50) Integer size) {

	public GigFilters filters() {
		return new GigFilters(categoryId, minPay, maxPay, payType, experience, startFrom, q);
	}
}
