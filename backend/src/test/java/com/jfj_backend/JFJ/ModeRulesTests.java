package com.jfj_backend.JFJ;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.ProviderOnly;
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.security.WorkerOnly;

// There are no real provider/worker endpoints yet, so this test adds two tiny ones.
@Import({ TestcontainersConfiguration.class, ModeRulesTests.ModeTestController.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModeRulesTests {

	@RestController
	static class ModeTestController {

		@ProviderOnly
		@GetMapping("/api/v1/test-mode/provider")
		String providerAction() {
			return "ok";
		}

		@WorkerOnly
		@GetMapping("/api/v1/test-mode/worker")
		String workerAction() {
			return "ok";
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	TokenService tokenService;

	private User user;

	@BeforeEach
	void createUser() {
		user = users.saveAndFlush(new User("Nila", "nila@example.com", "9456789012", "$2a$10$hash",
				LocalDate.of(1994, 3, 3), "9 River Road", "Jorhat", "Assam", "785001"));
	}

	private String bearer(Mode mode) {
		return "Bearer " + tokenService.createAccessToken(user, mode, Instant.now());
	}

	@Test
	void workerModeCanUseWorkerEndpointsOnly() throws Exception {
		mockMvc.perform(get("/api/v1/test-mode/worker").header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/test-mode/provider").header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isForbidden());
	}

	@Test
	void providerModeCanUseProviderEndpointsOnly() throws Exception {
		mockMvc.perform(get("/api/v1/test-mode/provider").header(HttpHeaders.AUTHORIZATION, bearer(Mode.PROVIDER)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/test-mode/worker").header(HttpHeaders.AUTHORIZATION, bearer(Mode.PROVIDER)))
				.andExpect(status().isForbidden());
	}

	@Test
	void noTokenIs401NotForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/test-mode/provider")).andExpect(status().isUnauthorized());
	}
}
