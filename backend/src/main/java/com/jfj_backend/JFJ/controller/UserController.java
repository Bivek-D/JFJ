package com.jfj_backend.JFJ.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.response.MeResponse;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.service.UserService;
import com.jfj_backend.JFJ.service.XpService;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

	private final UserService userService;
	private final XpService xpService;

	public UserController(UserService userService, XpService xpService) {
		this.userService = userService;
		this.xpService = xpService;
	}

	/** @AuthenticationPrincipal hands us the already-verified access token of this request. */
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		Mode mode = Mode.valueOf(jwt.getClaimAsString("mode"));
		User user = userService.getById(userId);
		return MeResponse.from(user, mode, xpService.levelInfo(user));
	}
}
