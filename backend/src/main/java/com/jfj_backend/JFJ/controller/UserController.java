package com.jfj_backend.JFJ.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.dto.response.MeResponse;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	/** @AuthenticationPrincipal hands us the already-verified access token of this request. */
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		Mode mode = Mode.valueOf(jwt.getClaimAsString("mode"));
		return MeResponse.from(userService.getById(userId), mode);
	}
}
