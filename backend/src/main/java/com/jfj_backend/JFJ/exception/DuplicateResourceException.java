package com.jfj_backend.JFJ.exception;

/** Something that must be unique already exists (e.g. email). Becomes HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String message) {
		super(message);
	}
}
