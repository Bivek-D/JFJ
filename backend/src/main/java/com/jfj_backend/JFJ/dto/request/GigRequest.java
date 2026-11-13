package com.jfj_backend.JFJ.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Date rules that need "today" (start not in the past, deadline not after start) are checked in GigService.
 *
 * lat/lng are limited to a box around India. Besides keeping gigs local, this catches the most common
 * map bug: lat and lng swapped (Guwahati as lat 91, lng 26 lands outside the box).
 */
public record GigRequest(
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 2000) String description,
		@NotNull Long categoryId,
		@NotNull PayType payType,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2,
				message = "must have at most 2 decimal places") BigDecimal payAmount,
		@NotNull @Min(1) @Max(50) Integer workersNeeded,
		ExperienceLevel minExperience,
		@NotNull LocalDate startDate,
		@NotBlank @Size(max = 100) String durationText,
		LocalDate applyDeadline,
		@Size(max = 10) Set<@NotNull Long> skillIds,
		@NotNull @DecimalMin(value = "6.0", message = "must be inside India")
		@DecimalMax(value = "38.0", message = "must be inside India") Double lat,
		@NotNull @DecimalMin(value = "68.0", message = "must be inside India")
		@DecimalMax(value = "98.0", message = "must be inside India") Double lng,
		@NotBlank @Size(max = 255) String addressLine,
		@NotBlank @Size(max = 100) String locality,
		@NotBlank @Size(max = 100) String city,
		@Min(1) @Max(5) Integer minLevel) {
}
