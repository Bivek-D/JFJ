package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;
import com.sun.net.httpserver.HttpServer;

/**
 * Geoapify is replaced by a tiny HTTP server inside the test (the JDK's built-in HttpServer), so tests
 * never call the real service or need a key. It answers like Geoapify and records what was asked.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GeoTests {

	private static final HttpServer FAKE_GEOAPIFY;
	private static final List<String> CALLS = new CopyOnWriteArrayList<>();
	private static volatile int nextStatus = 200;
	private static volatile String nextBody = "";

	static {
		try {
			FAKE_GEOAPIFY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		FAKE_GEOAPIFY.createContext("/", exchange -> {
			CALLS.add(exchange.getRequestURI().toString());
			byte[] bytes = nextBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(nextStatus, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		FAKE_GEOAPIFY.start();
	}

	@DynamicPropertySource
	static void geoapify(DynamicPropertyRegistry registry) {
		registry.add("geoapify.base-url", () -> "http://127.0.0.1:" + FAKE_GEOAPIFY.getAddress().getPort());
		registry.add("geoapify.api-key", () -> "test-key");
		registry.add("geoapify.requests-per-minute", () -> "5");
	}

	@AfterAll
	static void stop() {
		FAKE_GEOAPIFY.stop(0);
	}

	private static final String ZOO_ROAD = """
			{"type": "FeatureCollection", "features": [
			  {"type": "Feature", "properties": {
			     "formatted": "Zoo Road, Guwahati, Assam 781024, India", "address_line1": "Zoo Road",
			     "suburb": "Zoo Tiniali", "city": "Guwahati", "state": "Assam", "postcode": "781024",
			     "lat": 26.1726, "lon": 91.7800}},
			  {"type": "Feature", "properties": {"formatted": "No coordinates here"}}
			]}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	TokenService tokenService;

	private User user;

	@BeforeEach
	void setUp() {
		CALLS.clear();
		nextStatus = 200;
		nextBody = ZOO_ROAD;
		// A different user per test: each starts with a fresh rate-limit window.
		long n = System.nanoTime() % 1_000_000_000L;
		user = users.saveAndFlush(new User("Meena", "meena" + n + "@example.com", "9" + String.format("%09d", n),
				"$2a$10$hash", LocalDate.of(1985, 1, 10), "8 Station Road", "Guwahati", "Assam", "781001"));
	}

	private ResultActions call(String path) throws Exception {
		return mockMvc.perform(get("/api/v1/geo" + path).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenService.createAccessToken(user, Mode.PROVIDER, Instant.now())));
	}

	@Test
	void autocompleteReturnsOurShapeAndSendsTheRightQuery() throws Exception {
		call("/autocomplete?q=zoo road unique1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))   // the result without coordinates is skipped
				.andExpect(jsonPath("$[0].label").value("Zoo Road, Guwahati, Assam 781024, India"))
				.andExpect(jsonPath("$[0].addressLine").value("Zoo Road"))
				.andExpect(jsonPath("$[0].locality").value("Zoo Tiniali"))
				.andExpect(jsonPath("$[0].city").value("Guwahati"))
				.andExpect(jsonPath("$[0].pincode").value("781024"))
				.andExpect(jsonPath("$[0].lat").value(26.1726))
				.andExpect(jsonPath("$[0].lng").value(91.78));

		assertThat(CALLS).hasSize(1);
		assertThat(CALLS.getFirst()).startsWith("/v1/geocode/autocomplete?")
				.contains("text=zoo%20road%20unique1", "filter=countrycode:in", "limit=5", "apiKey=test-key");
	}

	@Test
	void sameSearchIsServedFromTheCache() throws Exception {
		call("/autocomplete?q=cached search").andExpect(status().isOk());
		call("/autocomplete?q=Cached Search ").andExpect(status().isOk());
		assertThat(CALLS).hasSize(1);
	}

	@Test
	void reverseRoundsThePointAndReturnsOneAddress() throws Exception {
		call("/reverse?lat=26.17261234&lng=91.78004321")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.addressLine").value("Zoo Road"));
		assertThat(CALLS.getFirst()).startsWith("/v1/geocode/reverse?").contains("lat=26.1726", "lon=91.78");

		nextBody = "{\"type\": \"FeatureCollection\", \"features\": []}";
		call("/reverse?lat=27.5&lng=95.5").andExpect(status().isNotFound());
	}

	@Test
	void geoapifyFailureIs503() throws Exception {
		nextStatus = 401;
		nextBody = "{\"error\": \"Invalid apiKey\"}";
		call("/autocomplete?q=failing search")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("drop the pin")));
	}

	@Test
	void sixthCallInAMinuteIs429() throws Exception {
		for (int i = 1; i <= 5; i++) {
			call("/autocomplete?q=search number " + i).andExpect(status().isOk());
		}
		call("/autocomplete?q=search number 6").andExpect(status().isTooManyRequests());
	}

	@Test
	void badInputIs400AndLoginIsRequired() throws Exception {
		call("/autocomplete?q=ab").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.q").exists());
		call("/reverse?lat=91.7&lng=26.1").andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/geo/autocomplete?q=zoo road")).andExpect(status().isUnauthorized());
		assertThat(CALLS).isEmpty();
	}
}
