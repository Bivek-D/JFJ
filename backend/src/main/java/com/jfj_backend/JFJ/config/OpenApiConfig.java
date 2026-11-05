package com.jfj_backend.JFJ.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger / OpenAPI: the API contract for the frontend (PRD section 16).
 * springdoc reads the controllers and DTOs and builds the spec; this adds the title and the
 * "bearerAuth" scheme, so the UI's Authorize button can send "Authorization: Bearer <token>".
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI jfjOpenApi() {
		return new OpenAPI()
				.info(new Info().title("JFJ API").version("v1")
						.description("Job For Jobless backend. Log in, copy accessToken, click Authorize."))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}
}
