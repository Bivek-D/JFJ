package com.jfj_backend.JFJ.dto.response;

import com.jfj_backend.JFJ.enums.Mode;

/** Login result in the body. The refresh token is NOT here: it goes in an HttpOnly cookie. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Mode mode) {
}
