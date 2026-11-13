package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Only "not blank" here: any stricter rule would hint which part is wrong. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
