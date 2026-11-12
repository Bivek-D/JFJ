package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
		Map<String, String> fieldErrors) {
}
