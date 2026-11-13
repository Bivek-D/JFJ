package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.repository.RefreshTokenRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSessionTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtDecoder jwtDecoder;

	record Session(String accessToken, String refreshToken) {
	}

	@BeforeEach
	void createUser() {
		User u = new User("Kiran", "kiran@example.com", "9345678901", passwordEncoder.encode("secret123"),
				LocalDate.of(1992, 6, 1), "3 Lake Road", "Tezpur", "Assam", "784001");
		u.setEmailVerified(true);
		users.saveAndFlush(u);
	}

	private static Session sessionFrom(MvcResult result) throws Exception {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		String refresh = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
		String access = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
		return new Session(access, refresh);
	}

	private Session login() throws Exception {
		return sessionFrom(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"kiran@example.com\", \"password\": \"secret123\"}")).andReturn());
	}

	private String modeOf(String accessToken) {
		return jwtDecoder.decode(accessToken).getClaimAsString("mode");
	}

	@Test
	void refreshRotatesTheCookieAndRevokesTheOldOne() throws Exception {
		Session first = login();

		MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", first.refreshToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists())
				.andReturn();
		Session second = sessionFrom(result);

		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		assertThat(refreshTokens.findByTokenHash(TokenService.hash(first.refreshToken())).orElseThrow().isRevoked()).isTrue();

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", first.refreshToken())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Session expired, please log in again"));
	}

	@Test
	void refreshWithoutCookieIs401() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesAndClearsTheCookie() throws Exception {
		Session session = login();

		MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", session.refreshToken())))
				.andExpect(status().isNoContent())
				.andReturn();
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=", "Max-Age=0");

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", session.refreshToken())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutWithoutCookieStillSucceeds() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
	}

	@Test
	void switchModeSurvivesARefresh() throws Exception {
		Session session = login();
		assertThat(modeOf(session.accessToken())).isEqualTo("WORKER");

		MvcResult switched = mockMvc.perform(post("/api/v1/auth/switch-mode")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
				.cookie(new Cookie("refresh_token", session.refreshToken()))
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\": \"PROVIDER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("PROVIDER"))
				.andReturn();
		String providerToken = JsonPath.read(switched.getResponse().getContentAsString(), "$.accessToken");
		assertThat(modeOf(providerToken)).isEqualTo("PROVIDER");
		assertThat(refreshTokens.findByTokenHash(TokenService.hash(session.refreshToken())).orElseThrow().getMode())
				.isEqualTo(Mode.PROVIDER);

		Session reloaded = sessionFrom(mockMvc.perform(post("/api/v1/auth/refresh")
				.cookie(new Cookie("refresh_token", session.refreshToken()))).andReturn());
		assertThat(modeOf(reloaded.accessToken())).isEqualTo("PROVIDER");

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + reloaded.accessToken()))
				.andExpect(jsonPath("$.mode").value("PROVIDER"));
	}

	@Test
	void switchModeNeedsAnAccessToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/switch-mode").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\": \"PROVIDER\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void switchModeRejectsUnknownMode() throws Exception {
		Session session = login();
		mockMvc.perform(post("/api/v1/auth/switch-mode")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\": \"ADMIN\"}"))
				.andExpect(status().isBadRequest());
	}
}
