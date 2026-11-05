package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.Map;

/** The one error shape every endpoint returns (PRD section 11). fieldErrors is empty unless input was invalid. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
		Map<String, String> fieldErrors) {
}
