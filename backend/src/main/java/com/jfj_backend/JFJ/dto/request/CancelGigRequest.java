package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/gigs/{id}/cancel. A reason is required (GIG-3): applicants and workers see it. */
public record CancelGigRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
}
