package com.jfj_backend.JFJ.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.jfj_backend.JFJ.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns exceptions thrown by controllers and services into the PRD's error body.
 * Controllers and services just throw; this class decides the HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** @Valid failed: one message per bad field, e.g. {"email": "must be a well-formed email address"}. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return build(HttpStatus.BAD_REQUEST, "Some fields are invalid", request, fieldErrors);
	}

	/** Body missing or not valid JSON (e.g. a date in the wrong format). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request, Map.of());
	}

	/** A path or query value of the wrong type, e.g. /workers/abc where a UUID is expected. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", request, Map.of());
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
	}

	@ExceptionHandler(DuplicateResourceException.class)
	ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
	}

	@ExceptionHandler(UnauthorizedException.class)
	ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, Map.of());
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, Map.of());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
			Map<String, String> fieldErrors) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI(), fieldErrors);
		return ResponseEntity.status(status).body(body);
	}
}
