package com.jfj_backend.JFJ.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.jfj_backend.JFJ.entity.User;

import jakarta.persistence.LockModeType;

/**
 * Database access for users. Spring Data writes the SQL from the method names,
 * e.g. existsByEmail -> SELECT ... WHERE email = ?. Pass email lowercase and phone as 10 digits.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

	/** Loads and locks the user row (SELECT ... FOR UPDATE) for rating and XP changes (PRD 10.2). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(UUID id);
}
