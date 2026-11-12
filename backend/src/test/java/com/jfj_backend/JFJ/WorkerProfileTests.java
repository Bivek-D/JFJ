package com.jfj_backend.JFJ;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.entity.Skill;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.repository.SkillRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkerProfileTests {

	private static final String PROFILE = "/api/v1/workers/me/profile";
	private static final String SKILLS = "/api/v1/workers/me/skills";

	private static final String VALID_PROFILE = """
			{"photoUrl": "https://example.com/me.jpg", "description": "Hard working",
			 "languages": ["Assamese", " hindi ", "Hindi"], "experienceYears": 3,
			 "experienceLevel": "INTERMEDIATE"}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	SkillRepository skills;

	@Autowired
	TokenService tokenService;

	private User user;

	@BeforeEach
	void createUser() {
		user = users.saveAndFlush(new User("Ritu", "ritu@example.com", "9567890123", "$2a$10$hash",
				LocalDate.of(1997, 8, 15), "4 Tea Garden Lane", "Dibrugarh", "Assam", "786001"));
	}

	private String bearer(Mode mode) {
		return "Bearer " + tokenService.createAccessToken(user, mode, Instant.now());
	}

	private Long skillId(String name) {
		return skills.findAllByOrderByNameAsc().stream().filter(s -> s.getName().equals(name))
				.map(Skill::getId).findFirst().orElseThrow();
	}

	private ResultActions putJson(String url, String body, Mode mode) throws Exception {
		return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer(mode))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void skillListIsPublicAndSorted() throws Exception {
		mockMvc.perform(get("/api/v1/skills"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(18)))
				.andExpect(jsonPath("$[0].name").value("Babysitting"))
				.andExpect(jsonPath("$[17].name").value("Tutoring"));
	}

	@Test
	void newUserGetsEmptyIncompleteProfile() throws Exception {
		mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ritu"))
				.andExpect(jsonPath("$.profileComplete").value(false))
				.andExpect(jsonPath("$.missing", contains("photo", "skill", "language")));
	}

	@Test
	void profileBecomesCompleteWithPhotoSkillAndLanguage() throws Exception {
		putJson(PROFILE, VALID_PROFILE, Mode.WORKER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.languages", contains("Assamese", "hindi")))
				.andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"))
				.andExpect(jsonPath("$.profileComplete").value(false))
				.andExpect(jsonPath("$.missing", contains("skill")));

		putJson(SKILLS, "{\"skillIds\": [" + skillId("Cooking") + ", " + skillId("Cleaning") + "]}", Mode.WORKER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skills[*].name", contains("Cleaning", "Cooking")))
				.andExpect(jsonPath("$.profileComplete").value(true))
				.andExpect(jsonPath("$.missing", hasSize(0)));

		putJson(SKILLS, "{\"skillIds\": []}", Mode.WORKER)
				.andExpect(jsonPath("$.profileComplete").value(false));
	}

	@Test
	void skillsCanBeSetBeforeTheProfile() throws Exception {
		putJson(SKILLS, "{\"skillIds\": [" + skillId("Driving") + "]}", Mode.WORKER)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skills[0].name").value("Driving"))
				.andExpect(jsonPath("$.missing", contains("photo", "language")));
	}

	@Test
	void editingNeedsWorkerModeButReadingDoesNot() throws Exception {
		putJson(PROFILE, VALID_PROFILE, Mode.PROVIDER).andExpect(status().isForbidden());
		putJson(SKILLS, "{\"skillIds\": []}", Mode.PROVIDER).andExpect(status().isForbidden());
		mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(Mode.PROVIDER)))
				.andExpect(status().isOk());
	}

	@Test
	void invalidProfileFieldsAre400() throws Exception {
		putJson(PROFILE, """
				{"photoUrl": "http://example.com/me.jpg", "languages": [], "experienceYears": -1,
				 "experienceLevel": "BEGINNER"}""", Mode.WORKER)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.photoUrl").value("must be an https link"))
				.andExpect(jsonPath("$.fieldErrors.experienceYears").exists());

		putJson(PROFILE, """
				{"languages": ["Hindi"], "experienceYears": 1, "experienceLevel": "GURU"}""", Mode.WORKER)
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownSkillIdIs400() throws Exception {
		putJson(SKILLS, "{\"skillIds\": [999999]}", Mode.WORKER)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("999999")));
	}

	@Test
	void publicProfileHidesPrivateDetails() throws Exception {
		putJson(PROFILE, VALID_PROFILE, Mode.WORKER).andExpect(status().isOk());

		String body = mockMvc.perform(get("/api/v1/workers/" + user.getId())
						.header(HttpHeaders.AUTHORIZATION, bearer(Mode.PROVIDER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ritu"))
				.andExpect(jsonPath("$.city").value("Dibrugarh"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body)
				.doesNotContain("ritu@example.com", "9567890123", "Tea Garden", "786001", "1997");
	}

	@Test
	void publicProfileIs404WithoutProfileOrForInactiveUser() throws Exception {
		mockMvc.perform(get("/api/v1/workers/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isNotFound());

		putJson(PROFILE, VALID_PROFILE, Mode.WORKER).andExpect(status().isOk());
		user.setStatus(UserStatus.SUSPENDED);
		users.saveAndFlush(user);
		mockMvc.perform(get("/api/v1/workers/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/workers/not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(Mode.WORKER)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void profileNeedsLogin() throws Exception {
		mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
	}
}
