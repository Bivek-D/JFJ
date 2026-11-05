package com.jfj_backend.JFJ.exception;

/** The thing asked for doesn't exist. Becomes HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}
}
