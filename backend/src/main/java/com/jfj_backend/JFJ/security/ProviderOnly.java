package com.jfj_backend.JFJ.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Put on a controller method (or class): only callers whose access token is in PROVIDER mode get in.
 * Others get 403 (PRD 11.1). MODE_PROVIDER comes from the token's "mode" claim (JwtConfig).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('MODE_PROVIDER')")
public @interface ProviderOnly {
}
