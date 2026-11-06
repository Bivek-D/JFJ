package com.jfj_backend.JFJ.exception;

/** 429: the caller sent too many requests in a short time (rate limit). */
public class TooManyRequestsException extends RuntimeException {

	public TooManyRequestsException(String message) {
		super(message);
	}
}
