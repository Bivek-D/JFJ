package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.util.AgeUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthRegisterTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	private ResultActions register(String email, String phone, String dateOfBirth) throws Exception {
		String body = """
				{"name": "Ravi Das", "email": "%s", "phone": "%s", "password": "secret123",
				 "dateOfBirth": "%s", "addressLine": "5 Station Road", "city": "Guwahati",
				 "state": "Assam", "pincode": "781001"}
				""".formatted(email, phone, dateOfBirth);
		return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void registersUserWithHashedPasswordAndLowercaseEmail() throws Exception {
		register("Ravi.Das@Example.com", "9123456780", "1998-04-10")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.email").value("ravi.das@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User saved = users.findByEmail("ravi.das@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
		assertThat(passwordEncoder.matches("secret123", saved.getPasswordHash())).isTrue();
		assertThat(saved.isEmailVerified()).isTrue();
	}

	@Test
	void sameEmailInDifferentCaseIs409() throws Exception {
		register("taken@example.com", "9123456781", "1998-04-10").andExpect(status().isCreated());

		register("TAKEN@example.com", "9123456782", "1998-04-10")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Email is already registered"))
				.andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
	}

	@Test
	void samePhoneIs409() throws Exception {
		register("first@example.com", "9123456783", "1998-04-10").andExpect(status().isCreated());

		register("second@example.com", "9123456783", "1998-04-10")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Phone number is already registered"));
	}

	@Test
	void underEighteenIs400() throws Exception {
		LocalDate seventeen = AgeUtils.todayInIndia().minusYears(17);

		register("young@example.com", "9123456784", seventeen.toString())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("You must be at least 18 years old to register"));
	}

	@Test
	void invalidFieldsAre400WithFieldErrors() throws Exception {
		register("not-an-email", "12345", "1998-04-10")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.phone").value("must be a 10-digit Indian mobile number without +91"));
	}
}
