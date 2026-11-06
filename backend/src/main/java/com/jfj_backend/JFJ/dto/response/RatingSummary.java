package com.jfj_backend.JFJ.dto.response;

/** average is null until the first rating; rounded to 1 decimal (4.25 -> 4.3). */
public record RatingSummary(Double average, int count) {

	public static RatingSummary of(int sum, int count) {
		return new RatingSummary(count == 0 ? null : Math.round(10.0 * sum / count) / 10.0, count);
	}
}
