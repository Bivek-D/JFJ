package com.jfj_backend.JFJ.exception;

/** Not logged in, or wrong credentials. Becomes HTTP 401. */
public class UnauthorizedException extends RuntimeException {

	public UnauthorizedException(String message) {
		super(message);
	}
}
