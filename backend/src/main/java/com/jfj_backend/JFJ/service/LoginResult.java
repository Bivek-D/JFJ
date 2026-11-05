package com.jfj_backend.JFJ.service;

import com.jfj_backend.JFJ.enums.Mode;

/** Everything login produces. The controller puts the refresh token in a cookie and the rest in the body. */
public record LoginResult(String accessToken, long expiresIn, Mode mode, String refreshToken) {
}
