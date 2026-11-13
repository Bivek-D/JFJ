package com.jfj_backend.JFJ.exception;

public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}
}
