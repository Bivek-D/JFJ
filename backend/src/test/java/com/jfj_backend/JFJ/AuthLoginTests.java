package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.jfj_backend.JFJ.entity.RefreshToken;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.repository.RefreshTokenRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthLoginTests {

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

	private User user;

	@BeforeEach
	void createUser() {
		User u = new User("Meera", "meera@example.com", "9234567890", passwordEncoder.encode("secret123"),
				LocalDate.of(1990, 1, 15), "7 Hill Road", "Shillong", "Meghalaya", "793001");
		u.setEmailVerified(true);
		user = users.saveAndFlush(u);
	}

	private ResultActions login(String email, String password) throws Exception {
		String body = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void loginReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
		MvcResult result = login("Meera@Example.com", "secret123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andExpect(jsonPath("$.mode").value("WORKER"))
				.andExpect(jsonPath("$.refreshToken").doesNotExist())
				.andReturn();

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refresh_token=")
				.contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=604800");

		String rawRefresh = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
		RefreshToken stored = refreshTokens.findByTokenHash(TokenService.hash(rawRefresh)).orElseThrow();
		assertThat(stored.getMode()).isEqualTo(Mode.WORKER);
		assertThat(stored.getUser().getId()).isEqualTo(user.getId());

		String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
		Jwt jwt = jwtDecoder.decode(accessToken);
		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
		assertThat(jwt.getClaimAsString("mode")).isEqualTo("WORKER");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void accessTokenOpensProtectedEndpoint() throws Exception {
		String accessToken = JsonPath.read(
				login("meera@example.com", "secret123").andReturn().getResponse().getContentAsString(), "$.accessToken");

		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("meera@example.com"))
				.andExpect(jsonPath("$.mode").value("WORKER"));
	}

	@Test
	void protectedEndpointWithoutTokenIs401() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void forgedTokenIs401() throws Exception {
		String fake = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJlLW5vdC1vdXJz";
		mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + fake))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
		login("meera@example.com", "wrong-pass1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
		login("nobody@example.com", "secret123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void suspendedUserCannotLogIn() throws Exception {
		user.setStatus(UserStatus.SUSPENDED);
		users.saveAndFlush(user);

		login("meera@example.com", "secret123")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("This account is not active"));
	}
}
