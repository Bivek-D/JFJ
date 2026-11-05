package com.jfj_backend.JFJ.exception;

/** The input is well-formed but breaks a business rule (e.g. under 18). Becomes HTTP 400. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
