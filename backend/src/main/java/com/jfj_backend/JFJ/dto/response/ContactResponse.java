package com.jfj_backend.JFJ.dto.response;

/** phoneVerified is always false for now (there is no phone OTP): the app should say "unverified number". */
public record ContactResponse(String role, String name, String phone, boolean phoneVerified,
		java.time.Instant availableUntil) {
}
