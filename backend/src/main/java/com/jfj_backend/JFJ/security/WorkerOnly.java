package com.jfj_backend.JFJ.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/** Like {@link ProviderOnly}, for WORKER mode (e.g. applying to a gig). */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('MODE_WORKER')")
public @interface WorkerOnly {
}
