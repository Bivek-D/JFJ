package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelGigRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
}
