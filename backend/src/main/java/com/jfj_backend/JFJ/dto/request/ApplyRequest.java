package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/gigs/{gigId}/applications: an optional note to the provider (APP-1). */
public record ApplyRequest(@Size(max = 500) String note) {
}
