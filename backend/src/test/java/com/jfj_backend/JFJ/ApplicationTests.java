package com.jfj_backend.JFJ;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationTests {

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

	private User provider;
	private User otherProvider;
	private User worker;
	private Gig gig;

	@BeforeEach
	void setUp() {
		provider = user("Meena", "meena@example.com", "9678901234");
		otherProvider = user("Arun", "arun@example.com", "9789012345");
		worker = user("Ritu", "ritu@example.com", "9567890123");
		completeProfile(worker, ExperienceLevel.INTERMEDIATE);
		gig = openGig(2, null);
	}

	private User user(String name, String email, String phone) {
		return users.saveAndFlush(new User(name, email, phone, "$2a$10$hash", LocalDate.of(1990, 1, 1),
				"1 Road", "Guwahati", "Assam", "781001"));
	}

	private void completeProfile(User user, ExperienceLevel level) {
		WorkerProfile profile = new WorkerProfile(user);
		profile.updateDetails("https://example.com/p.jpg", "Hard working", List.of("Hindi"), 3, level);
		profile.replaceSkills(Set.of(skills.findAllByOrderByNameAsc().getFirst()));
		profiles.saveAndFlush(profile);
	}

	private Gig openGig(int workersNeeded, ExperienceLevel minExperience) {
		Gig g = new Gig(provider, GeoUtils.point(26.1445, 91.7362), new SplittableRandom(1));
		g.updateDetails(categories.findAll().getFirst(), "Wedding helpers", "Serving food", PayType.DAILY,
				new BigDecimal("690.34"), workersNeeded, minExperience, AgeUtils.todayInIndia().plusDays(5), "1 day",
				null, "House 12, Lane 3", "Zoo Road", "Guwahati", Set.of());
		g.publish();
		return gigs.saveAndFlush(g);
	}

	private ResultActions as(User user, Mode mode, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokenService.createAccessToken(user, mode, Instant.now())));
	}

	private ResultActions apply(User who, Gig g) throws Exception {
		return as(who, Mode.WORKER, post("/api/v1/gigs/" + g.getId() + "/applications")
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"I can come early\"}"));
	}

	private String applyAndGetId(User who, Gig g) throws Exception {
		String json = apply(who, g).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private User newWorker(int n) {
		User w = user("Worker " + n, "w" + n + "@example.com", "90000000" + String.format("%02d", n));
		completeProfile(w, ExperienceLevel.BEGINNER);
		return w;
	}

	@Test
	void workerAppliesOnceWithANote() throws Exception {
		apply(worker, gig).andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPLIED"))
				.andExpect(jsonPath("$.note").value("I can come early"))
				.andExpect(jsonPath("$.gig.title").value("Wedding helpers"))
				.andExpect(jsonPath("$.assignment").doesNotExist());
		apply(worker, gig).andExpect(status().isConflict());

		as(worker, Mode.WORKER, get("/api/v1/applications/mine"))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].gig.payAmount").value(690.34));
	}

	@Test
	void applyRulesAreEnforced() throws Exception {
		as(worker, Mode.PROVIDER, post("/api/v1/gigs/" + gig.getId() + "/applications"))
				.andExpect(status().isForbidden());
		completeProfile(provider, ExperienceLevel.EXPERIENCED);
		apply(provider, gig).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("You can't apply to your own gig"));
		User noProfile = user("New", "new@example.com", "9111111111");
		apply(noProfile, gig).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Complete your profile")));
		apply(worker, openGig(1, ExperienceLevel.EXPERIENCED)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This gig needs experienced experience or more"));
		Gig draft = new Gig(provider, GeoUtils.point(26.1, 91.7), new SplittableRandom(2));
		draft.updateDetails(categories.findAll().getFirst(), "Draft", "Draft", PayType.FIXED, BigDecimal.TEN, 1,
				null, AgeUtils.todayInIndia().plusDays(5), "1 day", null, "a", "b", "c", Set.of());
		apply(worker, gigs.saveAndFlush(draft)).andExpect(status().isNotFound());
	}

	@Test
	void workerCanWithdrawWhilePending() throws Exception {
		String id = applyAndGetId(worker, gig);
		as(otherProvider, Mode.WORKER, delete("/api/v1/applications/" + id)).andExpect(status().isNotFound());
		as(worker, Mode.WORKER, delete("/api/v1/applications/" + id)).andExpect(status().isNoContent());
		as(worker, Mode.WORKER, delete("/api/v1/applications/" + id)).andExpect(status().isBadRequest());
	}

	@Test
	void providerReviewsShortlistsRejectsAndHires() throws Exception {
		String ritu = applyAndGetId(worker, gig);
		User second = newWorker(2);
		String other = applyAndGetId(second, gig);

		as(otherProvider, Mode.PROVIDER, get("/api/v1/gigs/" + gig.getId() + "/applications"))
				.andExpect(status().isForbidden());
		as(provider, Mode.PROVIDER, get("/api/v1/gigs/" + gig.getId() + "/applications"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].worker.name").value("Ritu"))
				.andExpect(jsonPath("$[1].worker.experienceLevel").value("INTERMEDIATE"))
				.andExpect(jsonPath("$[1].worker.phone").doesNotExist());

		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + ritu + "/shortlist"))
				.andExpect(jsonPath("$.status").value("SHORTLISTED"));
		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + other + "/reject"))
				.andExpect(jsonPath("$.status").value("REJECTED"));
		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + other + "/hire"))
				.andExpect(status().isBadRequest());

		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + ritu + "/hire"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIRED"))
				.andExpect(jsonPath("$.assignmentId").isNotEmpty());

		as(worker, Mode.WORKER, get("/api/v1/applications/mine"))
				.andExpect(jsonPath("$.items[0].status").value("HIRED"))
				.andExpect(jsonPath("$.items[0].assignment.status").value("ACTIVE"));
	}

	@Test
	void hiredWorkerSeesTheExactAddressAndOpeningsDrop() throws Exception {
		String id = applyAndGetId(worker, gig);
		as(worker, Mode.WORKER, get("/api/v1/gigs/" + gig.getId()))
				.andExpect(jsonPath("$.exact").doesNotExist())
				.andExpect(jsonPath("$.openingsLeft").value(2));

		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + id + "/hire")).andExpect(status().isOk());

		as(worker, Mode.WORKER, get("/api/v1/gigs/" + gig.getId()))
				.andExpect(jsonPath("$.openingsLeft").value(1))
				.andExpect(jsonPath("$.exact.addressLine").value("House 12, Lane 3"))
				.andExpect(jsonPath("$.exact.providerPhone").value("9678901234"));
	}

	@Test
	void fullGigRefusesMoreHiresAndApplications() throws Exception {
		Gig single = openGig(1, null);
		String first = applyAndGetId(worker, single);
		String second = applyAndGetId(newWorker(3), single);

		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + first + "/hire")).andExpect(status().isOk());
		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + second + "/hire"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("No openings left: all 1 places are filled"));
		apply(newWorker(4), single).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This gig has no openings left"));
	}

	@Test
	void gigCantBeEditedAfterTheFirstHire() throws Exception {
		String id = applyAndGetId(worker, gig);
		as(provider, Mode.PROVIDER, post("/api/v1/applications/" + id + "/hire")).andExpect(status().isOk());
		String body = """
				{"title": "Changed", "description": "x", "categoryId": %d, "payType": "DAILY", "payAmount": 1,
				 "workersNeeded": 2, "startDate": "%s", "durationText": "1 day", "lat": 26.2, "lng": 91.8,
				 "addressLine": "a", "locality": "b", "city": "c"}"""
				.formatted(categories.findAll().getFirst().getId(), AgeUtils.todayInIndia().plusDays(5));
		as(provider, Mode.PROVIDER, put("/api/v1/gigs/" + gig.getId()).contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("This gig can't be edited after a worker has been hired"));
	}
}
