package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.repository.CategoryRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.SkillRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.util.AgeUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GigTests {

	private static final String GIGS = "/api/v1/gigs";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	CategoryRepository categories;

	@Autowired
	SkillRepository skills;

	@Autowired
	TokenService tokenService;

	@Autowired
	GigRepository gigRepository;

	// Plain SQL, to check what PostGIS really stored. Only used right after a request that already flushed.
	@Autowired
	JdbcTemplate jdbc;

	private User provider;
	private User otherProvider;

	@BeforeEach
	void createUsers() {
		provider = users.saveAndFlush(new User("Meena", "meena@example.com", "9678901234", "$2a$10$hash",
				LocalDate.of(1985, 1, 10), "8 Station Road", "Guwahati", "Assam", "781001"));
		otherProvider = users.saveAndFlush(new User("Arun", "arun@example.com", "9789012345", "$2a$10$hash",
				LocalDate.of(1988, 2, 20), "2 Hill Side", "Shillong", "Meghalaya", "793001"));
	}

	private String bearer(User user, Mode mode) {
		return "Bearer " + tokenService.createAccessToken(user, mode, Instant.now());
	}

	private long categoryId(String name) {
		return categories.findAll().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow().getId();
	}

	private long skillId(String name) {
		return skills.findAllByOrderByNameAsc().stream().filter(s -> s.getName().equals(name)).findFirst()
				.orElseThrow().getId();
	}

	private String gigBody(double lat, double lng, String... overrides) {
		LocalDate start = AgeUtils.todayInIndia().plusDays(3);
		String body = """
				{"title": "Help at a wedding", "description": "Serving food and cleaning up after",
				 "categoryId": %d, "payType": "DAILY", "payAmount": 690.34, "workersNeeded": 3,
				 "minExperience": null, "startDate": "%s", "durationText": "2 days",
				 "applyDeadline": "%s", "skillIds": [%d], "lat": %s, "lng": %s,
				 "addressLine": "House 12, Lane 3", "locality": "Zoo Road", "city": "Guwahati"}"""
				.formatted(categoryId("Events"), start, start.minusDays(1), skillId("Cooking"), lat, lng);
		for (int i = 0; i < overrides.length; i += 2) {
			body = body.replaceFirst("\"" + overrides[i] + "\": [^,}]+", "\"" + overrides[i] + "\": " + overrides[i + 1]);
		}
		return body;
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			User user, Mode mode) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user, mode)));
	}

	private ResultActions create(String body) throws Exception {
		return send(post(GIGS).contentType(MediaType.APPLICATION_JSON).content(body), provider, Mode.PROVIDER);
	}

	private String createId() throws Exception {
		String json = create(gigBody(26.1445, 91.7362)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private ResultActions update(String id, String body) throws Exception {
		return send(put(GIGS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body), provider,
				Mode.PROVIDER);
	}

	@Test
	void categoriesArePublicWithOtherLast() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(12)))
				.andExpect(jsonPath("$[0].name").value("Household"))
				.andExpect(jsonPath("$[11].name").value("Other"));
	}

	@Test
	void createMakesADraftWithExactAndApproximatePoints() throws Exception {
		create(gigBody(26.1445, 91.7362))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.payAmount").value(690.34))
				.andExpect(jsonPath("$.category.name").value("Events"))
				.andExpect(jsonPath("$.skills[0].name").value("Cooking"))
				.andExpect(jsonPath("$.lat").value(26.1445))
				.andExpect(jsonPath("$.lng").value(91.7362))
				.andExpect(jsonPath("$.locationEditsLeft").value(3));

		// Measured by PostGIS itself, in metres on the real Earth shape (geography), not by our own formula.
		Double metres = jdbc.queryForObject(
				"SELECT ST_Distance(location::geography, public_location::geography) FROM gigs", Double.class);
		assertThat(metres).isBetween(297.0, 503.0);
		// And the stored point has lng as x: Guwahati's x is ~91.7.
		assertThat(jdbc.queryForObject("SELECT ST_X(location) FROM gigs", Double.class)).isEqualTo(91.7362);
	}

	@Test
	void onlyProviderModeCanCreate() throws Exception {
		send(post(GIGS).contentType(MediaType.APPLICATION_JSON).content(gigBody(26.1445, 91.7362)), provider,
				Mode.WORKER).andExpect(status().isForbidden());
	}

	@Test
	void invalidGigsAre400() throws Exception {
		create(gigBody(91.7362, 26.1445)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.lat").value("must be inside India"));
		create(gigBody(26.1445, 91.7362, "payAmount", "690.345")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.payAmount").value("must have at most 2 decimal places"));
		create(gigBody(26.1445, 91.7362, "payType", "\"WEEKLY\"")).andExpect(status().isBadRequest());
		create(gigBody(26.1445, 91.7362, "startDate", "\"2020-01-01\"")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The start date can't be in the past"));
		String late = "\"" + AgeUtils.todayInIndia().plusDays(10) + "\"";
		create(gigBody(26.1445, 91.7362, "applyDeadline", late)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The apply deadline must be on or before the start date"));
		create(gigBody(26.1445, 91.7362, "categoryId", "999999")).andExpect(status().isBadRequest());
	}

	@Test
	void publishMovesDraftToOpenOnce() throws Exception {
		String id = createId();
		send(post(GIGS + "/" + id + "/publish"), provider, Mode.PROVIDER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
		send(post(GIGS + "/" + id + "/publish"), provider, Mode.PROVIDER)
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyDraftsCanBeDeleted() throws Exception {
		String draft = createId();
		send(delete(GIGS + "/" + draft), provider, Mode.PROVIDER).andExpect(status().isNoContent());
		// A JPA query first writes pending changes (flush); plain JDBC would not see the delete yet.
		assertThat(gigRepository.count()).isZero();

		String open = createId();
		send(post(GIGS + "/" + open + "/publish"), provider, Mode.PROVIDER).andExpect(status().isOk());
		send(delete(GIGS + "/" + open), provider, Mode.PROVIDER).andExpect(status().isBadRequest());
	}

	@Test
	void someoneElsesGigIs403AndMissingIs404() throws Exception {
		String id = createId();
		send(put(GIGS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(gigBody(26.1445, 91.7362)),
				otherProvider, Mode.PROVIDER).andExpect(status().isForbidden());
		send(delete(GIGS + "/" + id), otherProvider, Mode.PROVIDER).andExpect(status().isForbidden());
		send(post(GIGS + "/" + UUID.randomUUID() + "/publish"), provider, Mode.PROVIDER)
				.andExpect(status().isNotFound());
	}

	@Test
	void locationCanMoveThreeTimesAndApproxChangesOnlyWithIt() throws Exception {
		String id = createId();
		String approxBefore = jdbc.queryForObject("SELECT ST_AsText(public_location) FROM gigs", String.class);

		update(id, gigBody(26.1445, 91.7362, "title", "\"Wedding helpers\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Wedding helpers"))
				.andExpect(jsonPath("$.locationEditsLeft").value(3));
		assertThat(jdbc.queryForObject("SELECT ST_AsText(public_location) FROM gigs", String.class))
				.isEqualTo(approxBefore);

		update(id, gigBody(26.15, 91.74)).andExpect(jsonPath("$.locationEditsLeft").value(2));
		assertThat(jdbc.queryForObject("SELECT ST_AsText(public_location) FROM gigs", String.class))
				.isNotEqualTo(approxBefore);
		update(id, gigBody(26.16, 91.75)).andExpect(jsonPath("$.locationEditsLeft").value(1));
		update(id, gigBody(26.17, 91.76)).andExpect(jsonPath("$.locationEditsLeft").value(0));
		update(id, gigBody(26.18, 91.77)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("The location can be changed at most 3 times"));
	}

	@Test
	void detailHidesTheAddressFromEveryoneButTheOwner() throws Exception {
		String id = createId();
		User worker = users.saveAndFlush(new User("Ritu", "ritu@example.com", "9567890123", "$2a$10$hash",
				LocalDate.of(1997, 8, 15), "4 Tea Garden Lane", "Guwahati", "Assam", "781005"));

		send(get(GIGS + "/" + id), worker, Mode.WORKER).andExpect(status().isNotFound());
		send(post(GIGS + "/" + id + "/publish"), provider, Mode.PROVIDER).andExpect(status().isOk());

		String body = send(get(GIGS + "/" + id), worker, Mode.WORKER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Help at a wedding"))
				.andExpect(jsonPath("$.locality").value("Zoo Road"))
				.andExpect(jsonPath("$.provider.name").value("Meena"))
				.andExpect(jsonPath("$.mine").value(false))
				.andExpect(jsonPath("$.exact").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("House 12", "9678901234", "26.1445", "91.7362");

		send(get(GIGS + "/" + id), provider, Mode.WORKER)
				.andExpect(jsonPath("$.mine").value(true))
				.andExpect(jsonPath("$.exact.lat").value(26.1445))
				.andExpect(jsonPath("$.exact.addressLine").value("House 12, Lane 3"))
				.andExpect(jsonPath("$.exact.providerPhone").value("9678901234"));

		send(get(GIGS + "/" + UUID.randomUUID()), worker, Mode.WORKER).andExpect(status().isNotFound());
	}

	@Test
	void mineListsOnlyMyGigsWithStatusFilter() throws Exception {
		String first = createId();
		createId();
		send(post(GIGS + "/" + first + "/publish"), provider, Mode.PROVIDER).andExpect(status().isOk());
		send(post(GIGS).contentType(MediaType.APPLICATION_JSON).content(gigBody(25.5788, 91.8933)),
				otherProvider, Mode.PROVIDER).andExpect(status().isCreated());

		send(get(GIGS + "/mine"), provider, Mode.PROVIDER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.totalItems").value(2));
		send(get(GIGS + "/mine?status=OPEN"), provider, Mode.PROVIDER)
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(first));
		send(get(GIGS + "/mine?size=1"), provider, Mode.PROVIDER)
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.totalPages").value(2));
	}
}
