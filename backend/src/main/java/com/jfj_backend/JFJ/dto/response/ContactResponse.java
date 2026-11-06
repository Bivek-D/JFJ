package com.jfj_backend.JFJ.dto.response;

/**
 * GET /assignments/{id}/contact (APP-7): the OTHER party of the assignment.
 * phoneVerified is false for now (no phone OTP yet): the app should say "unverified number".
 * availableUntil is null while the job is active, else the end of the 7-day window.
 */
public record ContactResponse(String role, String name, String phone, boolean phoneVerified,
		java.time.Instant availableUntil) {
}
