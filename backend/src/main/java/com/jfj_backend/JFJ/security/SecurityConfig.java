package com.jfj_backend.JFJ.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
// Turns on @PreAuthorize checks on methods, which @ProviderOnly and @WorkerOnly are built on.
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
			SecurityErrorHandler securityErrorHandler) throws Exception {
		http
				// No CSRF tokens: the API uses Bearer tokens, and the refresh cookie is SameSite=Lax.
				.csrf(csrf -> csrf.disable())
				// No server-side sessions: every request carries its own token.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// First matching rule wins: open paths first, everything else needs a login.
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						// Spring forwards unhandled errors to /error; keep it open so callers see the real status, not 401.
						.requestMatchers("/error").permitAll()
						// Swagger UI and the OpenAPI spec: the contract the frontend builds against.
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						// Skills and categories are reference data: signup and gig screens need them without a login.
						.requestMatchers(HttpMethod.GET, "/api/v1/skills", "/api/v1/categories").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
								"/api/v1/auth/logout").permitAll()
						.anyRequest().authenticated())
				// Read "Authorization: Bearer <token>", check it with JwtConfig's decoder, and turn its
				// claims into ROLE_ and MODE_ permissions. A missing or bad token on a protected path = 401.
				.oauth2ResourceServer(oauth -> oauth
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(securityErrorHandler)
						.accessDeniedHandler(securityErrorHandler))
				// 401/403 decided by Spring Security get the PRD error body too (SecurityErrorHandler).
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint(securityErrorHandler)
						.accessDeniedHandler(securityErrorHandler));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
