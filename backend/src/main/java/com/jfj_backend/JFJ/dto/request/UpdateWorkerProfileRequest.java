package com.jfj_backend.JFJ.dto.request;

import java.util.List;

import org.hibernate.validator.constraints.URL;

import com.jfj_backend.JFJ.enums.ExperienceLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** photoUrl must be https, so pages served over https don't load mixed content. */
public record UpdateWorkerProfileRequest(
		@URL(protocol = "https", message = "must be an https link") @Size(max = 500) String photoUrl,
		@Size(max = 1000) String description,
		@NotNull @Size(max = 5) List<@NotBlank @Size(max = 30) String> languages,
		@NotNull @Min(0) @Max(60) Integer experienceYears,
		@NotNull ExperienceLevel experienceLevel) {
}
