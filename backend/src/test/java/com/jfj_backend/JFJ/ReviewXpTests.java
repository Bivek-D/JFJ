package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.PayType;
import com.jfj_backend.JFJ.repository.CategoryRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.SkillRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.repository.WorkerProfileRepository;
import com.jfj_backend.JFJ.repository.XpEventRepository;
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.service.XpService;
import com.jfj_backend.JFJ.util.AgeUtils;
import com.jfj_backend.JFJ.util.GeoUtils;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewXpTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	WorkerProfileRepository profiles;

	@Autowired
	SkillRepository skills;

	@Autowired
	CategoryRepository categories;

	@Autowired
	GigRepository gigs;

	@Autowired
	XpEventRepository xpEvents;

	@Autowired
	XpService xpService;

	@Autowired
	TokenService tokenService;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	private User provider;
	private User worker;
	private User stranger;

	@BeforeEach
	void setUp() {
		provider = user("Meena", "meena@example.com", "9678901234");
		worker = user("Ritu", "ritu@example.com", "9567890123");
		completeProfileDirectly(worker);
		stranger = user("Arun", "arun@example.com", "9789012345");
	}

	private User user(String name, String email, String phone) {
		return users.saveAndFlush(new User(name, email, phone, "$2a$10$hash", LocalDate.of(1990, 1, 1), "1 Road",
				"Guwahati", "Assam", "781001"));
	}

	/** Straight into the DB, so no profile XP: tests start from 0 XP. */
	private void completeProfileDirectly(User user) {
		WorkerProfile profile = new WorkerProfile(user);
		profile.updateDetails("https://example.com/p.jpg", null, List.of("Hindi"), 1, ExperienceLevel.BEGINNER);
		profile.replaceSkills(Set.of(skills.findAllByOrderByNameAsc().getFirst()));
		profiles.saveAndFlush(profile);
	}

	private Gig openGig(Integer minLevel) {
		Gig g = new Gig(provider, GeoUtils.point(26.1445, 91.7362), new SplittableRandom(1));
		g.updateDetails(categories.findAll().getFirst(), "Wedding helpers", "Serving food", PayType.DAILY,
				new BigDecimal("500"), 3, null, AgeUtils.todayInIndia().plusDays(5), "1 day", null, "a", "b", "c",
				Set.of());
		g.changeMinLevel(minLevel);
		g.publish();
		return gigs.saveAndFlush(g);
	}

	private ResultActions as(User user, Mode mode, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenService.createAccessToken(user, mode, Instant.now())));
	}

	private String json(ResultActions actions, String path) throws Exception {
		return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path);
	}

	private String hire(User w, Gig g) throws Exception {
		String applicationId = json(as(w, Mode.WORKER, post("/api/v1/gigs/" + g.getId() + "/applications"))
				.andExpect(status().isCreated()), "$.id");
		return json(as(provider, Mode.PROVIDER, post("/api/v1/applications/" + applicationId + "/hire"))
				.andExpect(status().isOk()), "$.assignmentId");
	}

	private String completedJob() throws Exception {
		Gig g = openGig(null);
		String assignmentId = hire(worker, g);
		as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + g.getId() + "/start")).andExpect(status().isOk());
		as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + g.getId() + "/complete")).andExpect(status().isOk());
		return assignmentId;
	}

	private ResultActions review(User who, String assignmentId, int rating) throws Exception {
		return as(who, Mode.WORKER, post("/api/v1/assignments/" + assignmentId + "/reviews")
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\": " + rating + ", \"comment\": \"ok\"}"));
	}

	/** SQL changes inside the test transaction: flush Hibernate's pending writes first, clear its cache after. */
	private void sql(String statement, Object... args) {
		entityManager.flush();
		jdbc.update(statement, args);
		entityManager.clear();
	}

	private int totalXp(User user) {
		entityManager.flush();
		return jdbc.queryForObject("SELECT total_xp FROM users WHERE id = ?", Integer.class, user.getId());
	}

	/** The cached total always equals the sum of the events. */
	private void assertXpConsistent(User user) {
		assertThat((long) totalXp(user)).isEqualTo(xpEvents.sumPoints(user.getId()));
	}

	@Test
	void levelThresholdsMatchThePrd() {
		assertThat(xpService.levelFor(0)).isEqualTo(1);
		assertThat(xpService.levelFor(59)).isEqualTo(1);
		assertThat(xpService.levelFor(60)).isEqualTo(2);
		assertThat(xpService.levelFor(200)).isEqualTo(3);
		assertThat(xpService.levelFor(449)).isEqualTo(3);
		assertThat(xpService.levelFor(450)).isEqualTo(4);
		assertThat(xpService.levelFor(5000)).isEqualTo(5);
		assertThat(xpService.levelName(2)).isEqualTo("Trusted");
	}

	@Test
	void completingAGigAwardsXpOnce() throws Exception {
		completedJob();
		assertThat(totalXp(worker)).isEqualTo(20);
		assertThat(totalXp(provider)).isEqualTo(10);
		assertXpConsistent(worker);
		assertXpConsistent(provider);

		as(worker, Mode.WORKER, get("/api/v1/users/me"))
				.andExpect(jsonPath("$.level.level").value(1))
				.andExpect(jsonPath("$.level.name").value("Newcomer"))
				.andExpect(jsonPath("$.level.totalXp").value(20));
	}

	@Test
	void completingTheProfileGivesTenXpOnlyOnce() throws Exception {
		User fresh = user("Kiran", "kiran@example.com", "9345678901");
		String profile = """
				{"photoUrl": "https://example.com/k.jpg", "languages": ["Hindi"], "experienceYears": 1,
				 "experienceLevel": "BEGINNER"}""";
		long skill = skills.findAllByOrderByNameAsc().getFirst().getId();
		as(fresh, Mode.WORKER, put("/api/v1/workers/me/profile").contentType(MediaType.APPLICATION_JSON)
				.content(profile)).andExpect(status().isOk());
		assertThat(totalXp(fresh)).isZero();   // no skill yet: not complete

		as(fresh, Mode.WORKER, put("/api/v1/workers/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [" + skill + "]}")).andExpect(jsonPath("$.profileComplete").value(true));
		assertThat(totalXp(fresh)).isEqualTo(10);

		as(fresh, Mode.WORKER, put("/api/v1/workers/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": []}"));
		as(fresh, Mode.WORKER, put("/api/v1/workers/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [" + skill + "]}"));
		assertThat(totalXp(fresh)).isEqualTo(10);
		assertXpConsistent(fresh);
	}

	@Test
	void bothSidesReviewAndRatingsAndXpUpdate() throws Exception {
		String assignmentId = completedJob();

		review(worker, assignmentId, 4).andExpect(status().isCreated())
				.andExpect(jsonPath("$.direction").value("WORKER_TO_PROVIDER"))
				.andExpect(jsonPath("$.reviewerName").value("Ritu"))
				.andExpect(jsonPath("$.editableUntil").isNotEmpty());
		review(provider, assignmentId, 5).andExpect(status().isCreated())
				.andExpect(jsonPath("$.direction").value("PROVIDER_TO_WORKER"));

		// Provider: 10 (completion) + 5 (4 stars). Worker: 20 (completion) + 10 (5 stars).
		assertThat(totalXp(provider)).isEqualTo(15);
		assertThat(totalXp(worker)).isEqualTo(30);
		assertXpConsistent(provider);
		assertXpConsistent(worker);

		as(provider, Mode.PROVIDER, get("/api/v1/users/me"))
				.andExpect(jsonPath("$.providerRating.average").value(4.0))
				.andExpect(jsonPath("$.providerRating.count").value(1))
				.andExpect(jsonPath("$.workerRating.average").doesNotExist());
		as(stranger, Mode.PROVIDER, get("/api/v1/workers/" + worker.getId()))
				.andExpect(jsonPath("$.rating.average").value(5.0))
				.andExpect(jsonPath("$.level.name").value("Newcomer"))
				.andExpect(jsonPath("$.completedGigs").value(1));
		as(stranger, Mode.WORKER, get("/api/v1/users/" + worker.getId() + "/reviews"))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].rating").value(5));
		as(worker, Mode.WORKER, get("/api/v1/assignments/mine"))
				.andExpect(jsonPath("$.items[0].reviewedByMe").value(true));
	}

	@Test
	void reviewRulesAreEnforced() throws Exception {
		String done = completedJob();
		review(worker, done, 4).andExpect(status().isCreated());
		review(worker, done, 3).andExpect(status().isConflict());
		review(stranger, done, 5).andExpect(status().isNotFound());
		as(worker, Mode.WORKER, post("/api/v1/assignments/" + done + "/reviews")
				.contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 6}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").exists());

		String active = hire(worker, openGig(null));
		review(provider, active, 5).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("You can review once the job has ended"));

		sql("UPDATE assignments SET ended_at = ? WHERE id = ?::uuid",
				Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)), done);
		review(provider, done, 5).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Reviews are only possible for 14 days after the job ends"));
	}

	@Test
	void editingWithin24HoursMovesRatingAndXpByTheDifference() throws Exception {
		String assignmentId = completedJob();
		String reviewId = json(review(provider, assignmentId, 5).andExpect(status().isCreated()), "$.id");
		assertThat(totalXp(worker)).isEqualTo(30);

		as(provider, Mode.PROVIDER, put("/api/v1/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\": 2, \"comment\": \"Came late\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(2))
				.andExpect(jsonPath("$.editCount").value(1));
		// 30 + (xp(2) - xp(5)) = 30 + (-5 - 10) = 15. The count stays 1, the average is now 2.0.
		assertThat(totalXp(worker)).isEqualTo(15);
		assertXpConsistent(worker);
		as(worker, Mode.WORKER, get("/api/v1/users/me"))
				.andExpect(jsonPath("$.workerRating.average").value(2.0))
				.andExpect(jsonPath("$.workerRating.count").value(1));

		as(worker, Mode.WORKER, put("/api/v1/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\": 5}")).andExpect(status().isNotFound());
		sql("UPDATE reviews SET created_at = ? WHERE id = ?::uuid",
				Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)), reviewId);
		as(provider, Mode.PROVIDER, put("/api/v1/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rating\": 4}")).andExpect(status().isForbidden());
	}

	@Test
	void xpNeverGoesBelowZero() throws Exception {
		// Cancelled gig: the worker is REMOVED, gets no completion XP, so has 0 XP.
		Gig g = openGig(null);
		String assignmentId = hire(worker, g);
		as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + g.getId() + "/cancel")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Event called off\"}"));
		review(provider, assignmentId, 1).andExpect(status().isCreated());   // -10 wanted

		assertThat(totalXp(worker)).isZero();
		assertXpConsistent(worker);   // the event was stored as 0 (clamped), so the sum matches
		assertThat(jdbc.queryForObject("SELECT points FROM xp_events WHERE user_id = ?", Integer.class,
				worker.getId())).isZero();
	}

	@Test
	void gigMinLevelBlocksLowerLevelWorkers() throws Exception {
		Gig trustedOnly = openGig(2);
		as(worker, Mode.WORKER, post("/api/v1/gigs/" + trustedOnly.getId() + "/applications"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This gig needs level 2 (Trusted) or higher"));

		sql("UPDATE users SET level = 2, total_xp = 60 WHERE id = ?", worker.getId());
		as(worker, Mode.WORKER, post("/api/v1/gigs/" + trustedOnly.getId() + "/applications"))
				.andExpect(status().isCreated());
		as(worker, Mode.WORKER, get("/api/v1/gigs/" + trustedOnly.getId()))
				.andExpect(jsonPath("$.minLevel").value(2))
				.andExpect(jsonPath("$.provider.level.name").value("Newcomer"));
	}

	@Test
	void applicantsCanBeSortedByRatingOrLevel() throws Exception {
		User star = user("Star", "star@example.com", "9111111111");
		completeProfileDirectly(star);
		User veteran = user("Veteran", "vet@example.com", "9222222222");
		completeProfileDirectly(veteran);
		Gig g = openGig(null);
		for (User w : List.of(worker, star, veteran)) {
			as(w, Mode.WORKER, post("/api/v1/gigs/" + g.getId() + "/applications")).andExpect(status().isCreated());
		}
		sql("UPDATE users SET rating_sum = 10, rating_count = 2 WHERE id = ?", star.getId());     // 5.0
		sql("UPDATE users SET rating_sum = 6, rating_count = 2, level = 4, total_xp = 500 WHERE id = ?",
				veteran.getId());                                                                    // 3.0, Pro

		String url = "/api/v1/gigs/" + g.getId() + "/applications?sort=";
		as(provider, Mode.PROVIDER, get(url + "RATING"))
				.andExpect(jsonPath("$[*].worker.name", contains("Star", "Veteran", "Ritu")));   // unrated last
		as(provider, Mode.PROVIDER, get(url + "LEVEL"))
				.andExpect(jsonPath("$[0].worker.name").value("Veteran"))
				.andExpect(jsonPath("$[0].worker.level.name").value("Pro"));
	}

	@Test
	void pendingReviewsListsWorkersNotYetRated() throws Exception {
		String assignmentId = completedJob();
		UUID gigId = UUID.fromString(jdbc.queryForObject("SELECT gig_id::text FROM assignments WHERE id = ?::uuid",
				String.class, assignmentId));
		String url = "/api/v1/gigs/" + gigId + "/reviews/pending";

		as(provider, Mode.PROVIDER, get(url))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].workerName").value("Ritu"))
				.andExpect(jsonPath("$[0].reviewOpenUntil").isNotEmpty());
		review(provider, assignmentId, 5).andExpect(status().isCreated());
		as(provider, Mode.PROVIDER, get(url)).andExpect(jsonPath("$", hasSize(0)));
		as(stranger, Mode.PROVIDER, get(url)).andExpect(status().isForbidden());
	}
}
