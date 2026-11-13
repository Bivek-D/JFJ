package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Minimum 3 characters: shorter queries give too many useless matches. */
public record AutocompleteParams(@NotBlank @Size(min = 3, max = 100) String q) {
}
