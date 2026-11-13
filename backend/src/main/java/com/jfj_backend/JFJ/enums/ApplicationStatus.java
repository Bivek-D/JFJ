package com.jfj_backend.JFJ.enums;

public enum ApplicationStatus {
	APPLIED, SHORTLISTED, HIRED, REJECTED, WITHDRAWN;

	public boolean isPending() {
		return this == APPLIED || this == SHORTLISTED;
	}
}
