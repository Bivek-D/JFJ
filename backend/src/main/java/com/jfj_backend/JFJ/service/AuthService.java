package com.jfj_backend.JFJ.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.request.LoginRequest;
import com.jfj_backend.JFJ.dto.request.RegisterRequest;
import com.jfj_backend.JFJ.entity.RefreshToken;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.DuplicateResourceException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.UnauthorizedException;
import com.jfj_backend.JFJ.repository.RefreshTokenRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.util.AgeUtils;

/** Account and session rules: register, login, refresh, logout, switch-mode. */
@Service
public class AuthService {

	private static final String BAD_CREDENTIALS = "Invalid email or password";
	private static final String SESSION_EXPIRED = "Session expired, please log in again";

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	// Checked when the email doesn't exist, so a wrong email takes as long as a wrong password.
	private final String dummyHash;

	// Constructor injection: Spring passes in the beans; fields stay final and tests can pass their own.
	public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
			TokenService tokenService) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyHash = passwordEncoder.encode("timing-equaliser");
	}

	@Transactional
	public User register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		if (!AgeUtils.isAdultOn(request.dateOfBirth(), AgeUtils.todayInIndia())) {
			throw new BadRequestException("You must be at least " + AgeUtils.MIN_AGE + " years old to register");
		}
		if (users.existsByEmail(email)) {
			throw new DuplicateResourceException("Email is already registered");
		}
		if (users.existsByPhone(request.phone())) {
			throw new DuplicateResourceException("Phone number is already registered");
		}

		User user = new User(request.name().trim(), email, request.phone(), passwordEncoder.encode(request.password()),
				request.dateOfBirth(), request.addressLine().trim(), request.city().trim(), request.state().trim(),
				request.pincode());
		user.setGender(request.gender());
		// Demo decision: no email OTP yet, so accounts count as verified at signup.
		user.setEmailVerified(true);

		try {
			// Flush now so a duplicate from two simultaneous signups fails here, not later.
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateResourceException("Email or phone number is already registered");
		}
	}

	/**
	 * Login lands in Worker mode (PRD 4.1). Wrong email and wrong password give the same 401,
	 * so nobody can use login to find out which emails are registered.
	 */
	@Transactional
	public LoginResult login(LoginRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = users.findByEmail(email).orElse(null);

		String hashToCheck = (user != null) ? user.getPasswordHash() : dummyHash;
		boolean passwordOk = passwordEncoder.matches(request.password(), hashToCheck);
		if (user == null || !passwordOk) {
			throw new UnauthorizedException(BAD_CREDENTIALS);
		}
		// Checked only after the password, so these messages never reveal anything to a stranger.
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ForbiddenException("This account is not active");
		}
		if (!user.isEmailVerified()) {
			throw new ForbiddenException("Please verify your email before logging in");
		}
		// Login lands in Worker mode (PRD 4.1).
		return startSession(user, Mode.WORKER, Instant.now());
	}

	/**
	 * Swap a refresh token for a new pair (rotation, AUTH-4). The old token stops working at once,
	 * so a stolen copy is useless after the real owner's next refresh. The session keeps its mode.
	 * Not yet: reuse detection and the grace window for simultaneous refreshes (after the demo).
	 */
	@Transactional
	public LoginResult refresh(String rawRefreshToken) {
		Instant now = Instant.now();
		RefreshToken current = findUsable(rawRefreshToken, now)
				.orElseThrow(() -> new UnauthorizedException(SESSION_EXPIRED));
		User user = current.getUser();
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ForbiddenException("This account is not active");
		}
		current.revoke();
		return startSession(user, current.getMode(), now);
	}

	/** End this session. Always succeeds, even with a missing or already-revoked token. */
	@Transactional
	public void logout(String rawRefreshToken) {
		findUsable(rawRefreshToken, Instant.now()).ifPresent(RefreshToken::revoke);
	}

	/**
	 * New access token in the other mode (AUTH-6). The session's refresh token remembers the mode,
	 * so a page reload (refresh) comes back in it.
	 */
	@Transactional
	public LoginResult switchMode(UUID userId, Mode mode, String rawRefreshToken) {
		Instant now = Instant.now();
		User user = users.findById(userId).orElseThrow(() -> new UnauthorizedException(SESSION_EXPIRED));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ForbiddenException("This account is not active");
		}
		// Only this user's own session is updated; a cookie from another account is ignored.
		findUsable(rawRefreshToken, now)
				.filter(token -> token.getUser().getId().equals(userId))
				.ifPresent(token -> token.setMode(mode));
		String accessToken = tokenService.createAccessToken(user, mode, now);
		return new LoginResult(accessToken, tokenService.accessTtlSeconds(), mode, null);
	}

	/** New access token + new refresh token (stored as a hash) in the given mode. */
	private LoginResult startSession(User user, Mode mode, Instant now) {
		String accessToken = tokenService.createAccessToken(user, mode, now);
		String refreshToken = tokenService.newRefreshToken();
		refreshTokens.save(new RefreshToken(user, TokenService.hash(refreshToken), mode, tokenService.refreshExpiry(now)));
		return new LoginResult(accessToken, tokenService.accessTtlSeconds(), mode, refreshToken);
	}

	private Optional<RefreshToken> findUsable(String rawRefreshToken, Instant now) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return Optional.empty();
		}
		return refreshTokens.findByTokenHash(TokenService.hash(rawRefreshToken))
				.filter(token -> token.isUsable(now));
	}
}
