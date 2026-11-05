package com.jfj_backend.JFJ;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;

// 401/403 decided by Spring Security (before any controller) must use the PRD error body too.
@Import({ TestcontainersConfiguration.class, ModeRulesTests.ModeTestController.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityErrorBodyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	TokenService tokenService;

	@Test
	void missingTokenIs401WithErrorBody() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Login required"))
				.andExpect(jsonPath("$.path").value("/api/v1/users/me"))
				.andExpect(jsonPath("$.timestamp").isString());
	}

	@Test
	void badTokenIs401WithErrorBody() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Access token is invalid or expired"));
	}

	@Test
	void wrongModeIs403WithErrorBody() throws Exception {
		User user = users.saveAndFlush(new User("Tara", "tara@example.com", "9567890123", "$2a$10$hash",
				LocalDate.of(1993, 8, 8), "4 Fort Road", "Dibrugarh", "Assam", "786001"));
		String workerToken = tokenService.createAccessToken(user, Mode.WORKER, Instant.now());

		mockMvc.perform(get("/api/v1/test-mode/provider").header(HttpHeaders.AUTHORIZATION, "Bearer " + workerToken))
				.andExpect(status().isForbidden())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.message").value("You can't do this in your current mode"))
				.andExpect(jsonPath("$.path").value("/api/v1/test-mode/provider"));
	}
}
