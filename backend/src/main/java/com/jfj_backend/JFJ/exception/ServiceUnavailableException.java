package com.jfj_backend.JFJ.exception;

/** 503: an outside service we depend on (e.g. Geoapify) is down, slow or not set up. Try again later. */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}
}
