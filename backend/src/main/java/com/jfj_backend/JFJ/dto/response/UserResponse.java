package com.jfj_backend.JFJ.dto.response;

import java.util.UUID;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Role;

/** What the API shows about a user. Never includes the password hash. */
public record UserResponse(UUID id, String name, String email, String phone, Role role, boolean emailVerified) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
				user.isEmailVerified());
	}
}
