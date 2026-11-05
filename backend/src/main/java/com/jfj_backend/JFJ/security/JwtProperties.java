package com.jfj_backend.JFJ.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The jwt.* settings from application.yml, as one typed object. "15m" and "7d" become Durations. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
