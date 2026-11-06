package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** GET /api/v1/geo/reverse?lat=..&lng=.. Same India box as gig locations. */
public record ReverseParams(
		@NotNull @DecimalMin(value = "6.0", message = "must be inside India")
		@DecimalMax(value = "38.0", message = "must be inside India") Double lat,
		@NotNull @DecimalMin(value = "68.0", message = "must be inside India")
		@DecimalMax(value = "98.0", message = "must be inside India") Double lng) {
}
