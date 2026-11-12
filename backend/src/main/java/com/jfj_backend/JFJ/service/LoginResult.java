package com.jfj_backend.JFJ.service;

import com.jfj_backend.JFJ.enums.Mode;

public record LoginResult(String accessToken, long expiresIn, Mode mode, String refreshToken) {
}
