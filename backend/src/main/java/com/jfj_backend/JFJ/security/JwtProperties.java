package com.jfj_backend.JFJ.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
