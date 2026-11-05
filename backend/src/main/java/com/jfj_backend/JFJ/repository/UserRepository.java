package com.jfj_backend.JFJ.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.User;

/**
 * Database access for users. Spring Data writes the SQL from the method names,
 * e.g. existsByEmail -> SELECT ... WHERE email = ?. Pass email lowercase and phone as 10 digits.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);
}
