package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/login. Only "not blank" here: any stricter rule would hint which part is wrong. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
