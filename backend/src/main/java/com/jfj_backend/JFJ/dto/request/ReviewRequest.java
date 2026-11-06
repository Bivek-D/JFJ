package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /assignments/{id}/reviews and PUT /reviews/{id}: 1-5 stars + optional comment. */
public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 500) String comment) {
}
