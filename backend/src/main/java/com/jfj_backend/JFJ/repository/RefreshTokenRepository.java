package com.jfj_backend.JFJ.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
