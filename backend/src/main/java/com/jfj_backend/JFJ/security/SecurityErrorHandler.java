package com.jfj_backend.JFJ.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.jfj_backend.JFJ.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security rejects some requests before they reach any controller, so GlobalExceptionHandler
 * never sees them. This writes those 401s and 403s in the same PRD error body as everything else.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	// Keeps the standard "WWW-Authenticate: Bearer ..." header that tells clients why a token failed.
	private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
	private final JsonMapper jsonMapper;

	public SecurityErrorHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** 401: no token, or a token that is forged, malformed or expired. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		bearerEntryPoint.commence(request, response, authException);
		String message = (authException instanceof InvalidBearerTokenException)
				? "Access token is invalid or expired"
				: "Login required";
		write(response, request, HttpStatus.UNAUTHORIZED, message);
	}

	/** 403: valid token, but not allowed, e.g. a Provider-only action in Worker mode. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, request, HttpStatus.FORBIDDEN, "You can't do this in your current mode");
	}

	private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
			throws IOException {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI(), Map.of());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}
}
