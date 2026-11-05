package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.jfj_backend.JFJ.enums.Mode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One login session (refresh_tokens table, V2). The browser holds the raw token in a cookie;
 * this row holds only its SHA-256 hash, plus the mode the session was last in.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// Many sessions (phone, laptop) can belong to one user. LAZY: the user row is only loaded if the code asks for it.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Mode mode;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(User user, String tokenHash, Mode mode, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.mode = mode;
		this.expiresAt = expiresAt;
	}

	/** A token works only if nobody revoked it and it hasn't expired. */
	public boolean isUsable(Instant now) {
		return !revoked && expiresAt.isAfter(now);
	}

	public void revoke() {
		this.revoked = true;
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isRevoked() {
		return revoked;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
