package com.jfj_backend.JFJ.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.dto.request.LoginRequest;
import com.jfj_backend.JFJ.dto.request.RegisterRequest;
import com.jfj_backend.JFJ.dto.response.TokenResponse;
import com.jfj_backend.JFJ.dto.response.UserResponse;
import com.jfj_backend.JFJ.security.JwtProperties;
import com.jfj_backend.JFJ.service.AuthService;
import com.jfj_backend.JFJ.service.LoginResult;

import jakarta.validation.Valid;

/** HTTP layer only: read the request, call the service, shape the response. No business rules here. */
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
		LoginResult result = authService.login(request);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
				.body(new TokenResponse(result.accessToken(), "Bearer", result.expiresIn(), result.mode()));
	}

	/**
	 * HttpOnly: JavaScript can't read it. Secure: HTTPS only (browsers allow localhost too).
	 * SameSite=Lax: not sent on POSTs from other sites, which blocks CSRF on /refresh.
	 */
	private ResponseCookie refreshCookie(String value) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(refreshTtl)
				.build();
	}
}
