package com.jfj_backend.JFJ.dto.response;

import java.util.UUID;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.Role;

/** GET /api/v1/users/me: the logged-in user plus the mode their current token is in. */
public record MeResponse(UUID id, String name, String email, String phone, Role role, boolean emailVerified,
		Mode mode, RatingSummary workerRating, RatingSummary providerRating, LevelInfo level) {

	public static MeResponse from(User user, Mode mode, LevelInfo level) {
		return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
				user.isEmailVerified(), mode, RatingSummary.of(user.getRatingSum(), user.getRatingCount()),
				RatingSummary.of(user.getProviderRatingSum(), user.getProviderRatingCount()), level);
	}
}
