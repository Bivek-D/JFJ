package com.jfj_backend.JFJ.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * geoapify.* in application.yml. apiKey may be empty: the app still starts, and the geo endpoints
 * answer 503 "not available" (the pin + typed address still work without them).
 */
@ConfigurationProperties("geoapify")
public record GeoapifyProperties(String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout,
		int requestsPerMinute, Duration cacheTtl) {

	public boolean configured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
