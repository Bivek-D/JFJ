package com.jfj_backend.JFJ.enums;

/** Account state. Only ACTIVE users can log in. Must match the CHECK list on users.status in V2. */
public enum UserStatus {
	ACTIVE,
	SUSPENDED,
	DELETED
}
