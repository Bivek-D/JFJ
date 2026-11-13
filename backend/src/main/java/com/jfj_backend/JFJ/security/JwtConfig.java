package com.jfj_backend.JFJ.security;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	public static final String ISSUER = "jfj";

	/** HS256 needs a key of at least 256 bits (32 bytes); a shorter one is easy to brute-force. */
	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	/**
	 * Turns the token's claims into authorities: role USER -> ROLE_USER, mode WORKER -> MODE_WORKER.
	 * @WorkerOnly and @ProviderOnly check these.
	 */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		Converter<Jwt, Collection<GrantedAuthority>> authorities = jwt -> List.of(
				new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")),
				new SimpleGrantedAuthority("MODE_" + jwt.getClaimAsString("mode")));
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
