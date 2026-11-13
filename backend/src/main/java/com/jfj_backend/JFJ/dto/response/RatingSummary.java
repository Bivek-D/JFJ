package com.jfj_backend.JFJ.dto.response;

public record RatingSummary(Double average, int count) {

	public static RatingSummary of(int sum, int count) {
		return new RatingSummary(count == 0 ? null : Math.round(10.0 * sum / count) / 10.0, count);
	}
}
