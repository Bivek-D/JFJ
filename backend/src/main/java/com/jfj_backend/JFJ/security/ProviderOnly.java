package com.jfj_backend.JFJ.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/** MODE_PROVIDER comes from the token's "mode" claim (see JwtConfig). */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('MODE_PROVIDER')")
public @interface ProviderOnly {
}
