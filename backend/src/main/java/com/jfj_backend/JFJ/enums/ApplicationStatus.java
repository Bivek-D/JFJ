package com.jfj_backend.JFJ.enums;

/** APPLIED -> SHORTLISTED -> HIRED, or REJECTED, or WITHDRAWN (PRD 4.4). */
public enum ApplicationStatus {
	APPLIED, SHORTLISTED, HIRED, REJECTED, WITHDRAWN;

	/** Still waiting for the provider's decision. */
	public boolean isPending() {
		return this == APPLIED || this == SHORTLISTED;
	}
}
