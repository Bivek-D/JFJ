package com.jfj_backend.JFJ.enums;

/**
 * Gig lifecycle (PRD 4.4): DRAFT -> OPEN -> IN_PROGRESS -> COMPLETED, plus CANCELLED (from OPEN or
 * IN_PROGRESS) and EXPIRED (from OPEN). Day 6 uses DRAFT and OPEN; the rest arrive with B4.
 */
public enum GigStatus {
	DRAFT, OPEN, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED
}
