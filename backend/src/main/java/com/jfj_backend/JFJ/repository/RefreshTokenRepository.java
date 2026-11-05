package com.jfj_backend.JFJ.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.RefreshToken;

/** Database access for refresh tokens. Lookups use the SHA-256 hash, never the raw token. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
