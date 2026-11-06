package com.jfj_backend.JFJ.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of GET /api/v1/gigs/map (MAP-2): the visible map area (from Leaflet's
 * getBounds(): south = minLat, west = minLng, north = maxLat, east = maxLng) plus the list's filters.
 */
public record GigMapParams(
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double minLat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double minLng,
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double maxLat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double maxLng,
		Long categoryId,
		@DecimalMin("0") BigDecimal minPay,
		@DecimalMin("0") BigDecimal maxPay,
		PayType payType,
		ExperienceLevel experience,
		LocalDate startFrom,
		@Size(max = 100) String q) {

	public GigFilters filters() {
		return new GigFilters(categoryId, minPay, maxPay, payType, experience, startFrom, q);
	}
}
