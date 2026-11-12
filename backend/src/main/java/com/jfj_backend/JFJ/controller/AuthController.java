package com.jfj_backend.JFJ.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.LoginRequest;
import com.jfj_backend.JFJ.dto.request.RegisterRequest;
import com.jfj_backend.JFJ.dto.request.SwitchModeRequest;
import com.jfj_backend.JFJ.dto.response.TokenResponse;
import com.jfj_backend.JFJ.dto.response.UserResponse;
import com.jfj_backend.JFJ.security.JwtProperties;
import com.jfj_backend.JFJ.service.AuthService;
import com.jfj_backend.JFJ.service.LoginResult;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	public static final String REFRESH_COOKIE = "refresh_token";
	// The browser sends the cookie only to auth endpoints, never to the rest of the API.
	private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private final AuthService authService;
	private final Duration refreshTtl;

	public AuthController(AuthService authService, JwtProperties jwtProperties) {
		this.authService = authService;
		this.refreshTtl = jwtProperties.refreshTtl();
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return UserResponse.from(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return withNewCookie(authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		return withNewCookie(authService.refresh(refreshToken));
	}

	/** Works with the cookie alone, so logging out still works after the access token expired. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearedCookie().toString()).build();
	}

	@PostMapping("/switch-mode")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	public TokenResponse switchMode(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SwitchModeRequest request,
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		LoginResult result = authService.switchMode(UUID.fromString(jwt.getSubject()), request.mode(), refreshToken);
		return toBody(result);
	}

	private ResponseEntity<TokenResponse> withNewCookie(LoginResult result) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
				.body(toBody(result));
	}

	private TokenResponse toBody(LoginResult result) {
		return new TokenResponse(result.accessToken(), "Bearer", result.expiresIn(), result.mode());
	}

	/**
	 * HttpOnly: JavaScript can't read it. Secure: HTTPS only (browsers allow localhost too).
	 * SameSite=Lax: not sent on POSTs from other sites, which blocks CSRF on /refresh.
	 */
	private ResponseCookie refreshCookie(String value) {
		return cookie(value, refreshTtl);
	}

	private ResponseCookie clearedCookie() {
		return cookie("", Duration.ZERO);
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(maxAge)
				.build();
	}
}
