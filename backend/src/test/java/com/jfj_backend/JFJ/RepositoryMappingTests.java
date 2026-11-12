package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.entity.RefreshToken;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.Role;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.repository.RefreshTokenRepository;
import com.jfj_backend.JFJ.repository.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RepositoryMappingTests {

	@Autowired
	UserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private User newUser(String email, String phone) {
		return new User("Asha", email, phone, "$2a$10$hash", LocalDate.of(1995, 5, 20),
				"12 MG Road", "Guwahati", "Assam", "781001");
	}

	@Test
	void savesUserWithDefaults() {
		User saved = users.saveAndFlush(newUser("asha@example.com", "9876543210"));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(users.findByEmail("asha@example.com")).isPresent();
		assertThat(users.existsByPhone("9876543210")).isTrue();
		assertThat(users.existsByEmail("nobody@example.com")).isFalse();
	}

	@Test
	void rejectsDuplicateEmail() {
		users.saveAndFlush(newUser("dup@example.com", "9000000001"));

		assertThatThrownBy(() -> users.saveAndFlush(newUser("dup@example.com", "9000000002")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void savesAndFindsRefreshTokenByHash() {
		User user = users.saveAndFlush(newUser("token@example.com", "9000000003"));
		String hash = "a".repeat(64);
		Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
		refreshTokens.saveAndFlush(new RefreshToken(user, hash, Mode.WORKER, expires));

		RefreshToken found = refreshTokens.findByTokenHash(hash).orElseThrow();
		assertThat(found.getMode()).isEqualTo(Mode.WORKER);
		assertThat(found.getUser().getId()).isEqualTo(user.getId());
		assertThat(found.isUsable(Instant.now())).isTrue();

		found.revoke();
		assertThat(found.isUsable(Instant.now())).isFalse();
	}
}
