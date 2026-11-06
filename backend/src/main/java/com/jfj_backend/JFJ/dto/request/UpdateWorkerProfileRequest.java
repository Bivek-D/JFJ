package com.jfj_backend.JFJ.dto.request;

import java.util.List;

import org.hibernate.validator.constraints.URL;

import com.jfj_backend.JFJ.enums.ExperienceLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/workers/me/profile. PUT = send every field; anything left out is cleared.
 * photoUrl is a plain link for the demo (uploads via Cloudinary come later); https only, so pages
 * served over https don't load mixed content.
 */
public record UpdateWorkerProfileRequest(
		@URL(protocol = "https", message = "must be an https link") @Size(max = 500) String photoUrl,
		@Size(max = 1000) String description,
		@NotNull @Size(max = 5) List<@NotBlank @Size(max = 30) String> languages,
		@NotNull @Min(0) @Max(60) Integer experienceYears,
		@NotNull ExperienceLevel experienceLevel) {
}
