package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** GET /api/v1/geo/autocomplete?q=zoo road. Under 3 characters gives too many useless matches. */
public record AutocompleteParams(@NotBlank @Size(min = 3, max = 100) String q) {
}
