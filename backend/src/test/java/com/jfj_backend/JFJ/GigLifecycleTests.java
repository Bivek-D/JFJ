package com.jfj_backend.JFJ;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

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
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.util.AgeUtils;
import com.jfj_backend.JFJ.util.GeoUtils;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GigLifecycleTests {

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
	TokenService tokenService;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager entityManager;

	private User provider;
	private User hiredWorker;
	private User waitingWorker;
	private User stranger;
	private Gig gig;
	private String hiredApplication;
	private String waitingApplication;
	private String assignmentId;

	@BeforeEach
	void setUp() throws Exception {
		provider = user("Meena", "meena@example.com", "9678901234");
		hiredWorker = worker("Ritu", "ritu@example.com", "9567890123");
		waitingWorker = worker("Kiran", "kiran@example.com", "9345678901");
		stranger = user("Arun", "arun@example.com", "9789012345");

		gig = new Gig(provider, GeoUtils.point(26.1445, 91.7362), new SplittableRandom(1));
		gig.updateDetails(categories.findAll().getFirst(), "Wedding helpers", "Serving food", PayType.DAILY,
				new BigDecimal("690.34"), 2, null, AgeUtils.todayInIndia().plusDays(5), "1 day", null,
				"House 12, Lane 3", "Zoo Road", "Guwahati", Set.of());
		gig.publish();
		gigs.saveAndFlush(gig);

		hiredApplication = applyAs(hiredWorker);
		waitingApplication = applyAs(waitingWorker);
		String hire = as(provider, Mode.PROVIDER, post("/api/v1/applications/" + hiredApplication + "/hire"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assignmentId = JsonPath.read(hire, "$.assignmentId");
	}

	private User user(String name, String email, String phone) {
		return users.saveAndFlush(new User(name, email, phone, "$2a$10$hash", LocalDate.of(1990, 1, 1), "1 Road",
				"Guwahati", "Assam", "781001"));
	}

	private User worker(String name, String email, String phone) {
		User u = user(name, email, phone);
		WorkerProfile profile = new WorkerProfile(u);
		profile.updateDetails("https://example.com/p.jpg", null, List.of("Hindi"), 1, ExperienceLevel.BEGINNER);
		profile.replaceSkills(Set.of(skills.findAllByOrderByNameAsc().getFirst()));
		profiles.saveAndFlush(profile);
		return u;
	}

	private String applyAs(User worker) throws Exception {
		String json = as(worker, Mode.WORKER, post("/api/v1/gigs/" + gig.getId() + "/applications"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private ResultActions as(User user, Mode mode, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenService.createAccessToken(user, mode, Instant.now())));
	}

	private ResultActions lifecycle(String action) throws Exception {
		return as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + gig.getId() + "/" + action));
	}

	private ResultActions cancel(String reason) throws Exception {
		return as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + gig.getId() + "/cancel")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + reason + "\"}"));
	}

	private ResultActions applicationsOf(User worker) throws Exception {
		return as(worker, Mode.WORKER, get("/api/v1/applications/mine"));
	}

	private ResultActions contact(User who) throws Exception {
		return as(who, Mode.WORKER, get("/api/v1/assignments/" + assignmentId + "/contact"));
	}

	@Test
	void startThenCompleteClosesEverything() throws Exception {
		lifecycle("complete").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only a gig in progress can be completed; start it first"));

		lifecycle("start").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		lifecycle("start").andExpect(status().isBadRequest());
		lifecycle("complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		as(hiredWorker, Mode.WORKER, get("/api/v1/assignments/mine"))
				.andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$.items[0].endedAt").isNotEmpty())
				.andExpect(jsonPath("$.items[0].gig.status").value("COMPLETED"));
		applicationsOf(waitingWorker).andExpect(jsonPath("$.items[0].status").value("REJECTED"));
		as(hiredWorker, Mode.WORKER, get("/api/v1/gigs/" + gig.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exact").doesNotExist());
		as(stranger, Mode.WORKER, get("/api/v1/gigs/" + gig.getId())).andExpect(status().isNotFound());
	}

	@Test
	void startNeedsAHire() throws Exception {
		Gig empty = new Gig(provider, GeoUtils.point(26.15, 91.74), new SplittableRandom(2));
		empty.updateDetails(categories.findAll().getFirst(), "Nobody hired", "x", PayType.FIXED, BigDecimal.TEN, 1,
				null, AgeUtils.todayInIndia().plusDays(5), "1 day", null, "a", "b", "c", Set.of());
		empty.publish();
		gigs.saveAndFlush(empty);
		as(provider, Mode.PROVIDER, post("/api/v1/gigs/" + empty.getId() + "/start"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Hire at least one worker before starting the gig"));
	}

	@Test
	void cancelRemovesHiresAndRejectsWaiting() throws Exception {
		cancel("no").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
		cancel("The wedding was postponed").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		cancel("Again please").andExpect(status().isBadRequest());

		as(hiredWorker, Mode.WORKER, get("/api/v1/assignments/mine"))
				.andExpect(jsonPath("$.items[0].status").value("REMOVED"))
				.andExpect(jsonPath("$.items[0].removedReason").value("gig cancelled"))
				.andExpect(jsonPath("$.items[0].cancelReason").value("The wedding was postponed"));
		applicationsOf(waitingWorker).andExpect(jsonPath("$.items[0].status").value("REJECTED"));
		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + waitingApplication + "/hire"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyTheOwnerInProviderModeRunsTheLifecycle() throws Exception {
		as(stranger, Mode.PROVIDER, post("/api/v1/gigs/" + gig.getId() + "/start")).andExpect(status().isForbidden());
		as(provider, Mode.WORKER, post("/api/v1/gigs/" + gig.getId() + "/start")).andExpect(status().isForbidden());
	}

	@Test
	void contactShowsTheOtherPartyWhileActive() throws Exception {
		contact(hiredWorker).andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("PROVIDER"))
				.andExpect(jsonPath("$.name").value("Meena"))
				.andExpect(jsonPath("$.phone").value("9678901234"))
				.andExpect(jsonPath("$.phoneVerified").value(false))
				.andExpect(jsonPath("$.availableUntil").doesNotExist());
		as(provider, Mode.PROVIDER, get("/api/v1/assignments/" + assignmentId + "/contact"))
				.andExpect(jsonPath("$.role").value("WORKER"))
				.andExpect(jsonPath("$.phone").value("9567890123"));
		contact(stranger).andExpect(status().isNotFound());
		contact(waitingWorker).andExpect(status().isNotFound());
	}

	@Test
	void contactLastsSevenDaysAfterCompletion() throws Exception {
		lifecycle("start").andExpect(status().isOk());
		lifecycle("complete").andExpect(status().isOk());
		contact(hiredWorker).andExpect(status().isOk()).andExpect(jsonPath("$.availableUntil").isNotEmpty());

		// Move the end 8 days into the past: the window has closed for both sides.
		// Hibernate keeps loaded entities in memory for the whole (test) transaction, so: write its pending
		// changes (flush), change the row with SQL, then make it forget its copies (clear) and re-read.
		entityManager.flush();
		jdbc.update("UPDATE assignments SET ended_at = ? WHERE id = ?::uuid",
				java.sql.Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)), assignmentId);
		entityManager.clear();
		contact(hiredWorker).andExpect(status().isForbidden());
		as(provider, Mode.PROVIDER, get("/api/v1/assignments/" + assignmentId + "/contact"))
				.andExpect(status().isForbidden());
	}

	@Test
	void removedWorkerLosesContactAtOnceButProviderKeepsIt() throws Exception {
		cancel("The wedding was postponed").andExpect(status().isOk());
		contact(hiredWorker).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("You no longer have access to this contact"));
		as(provider, Mode.PROVIDER, get("/api/v1/assignments/" + assignmentId + "/contact"))
				.andExpect(status().isOk());
	}
}
