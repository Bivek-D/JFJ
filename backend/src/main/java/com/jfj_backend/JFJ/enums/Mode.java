package com.jfj_backend.JFJ.enums;

/** Which side of the app a session is using. Must match the CHECK list on refresh_tokens.mode in V2. */
public enum Mode {
	WORKER,
	PROVIDER
}
