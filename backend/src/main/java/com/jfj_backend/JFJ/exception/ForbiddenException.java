package com.jfj_backend.JFJ.exception;

/** Logged in (or identified) but not allowed, e.g. a suspended account. Becomes HTTP 403. */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
