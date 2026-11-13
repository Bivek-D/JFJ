package com.jfj_backend.JFJ.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.Mode;

@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;
	private final SecureRandom random = new SecureRandom();

	public TokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	/** role and mode ride along in the token so requests need no database lookup to know them. */
	public String createAccessToken(User user, Mode mode, Instant now) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(JwtConfig.ISSUER)
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTtl()))
				.claim("role", user.getRole().name())
				.claim("mode", mode.name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public String newRefreshToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in Java", ex);
		}
	}

	public long accessTtlSeconds() {
		return properties.accessTtl().toSeconds();
	}

	public Instant refreshExpiry(Instant now) {
		return now.plus(properties.refreshTtl());
	}
}
