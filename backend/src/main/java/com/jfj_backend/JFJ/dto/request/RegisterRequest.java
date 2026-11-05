package com.jfj_backend.JFJ.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/register (AUTH-1). @Valid on the controller runs these checks
 * before any of our code; failures become a 400 with fieldErrors.
 * Unknown fields (e.g. lat/lng, not stored until B3) are ignored.
 */
public record RegisterRequest(
		@NotBlank @Size(max = 100) String name,

		@NotBlank @Email @Size(max = 254) String email,

		@NotBlank
		@Pattern(regexp = "[6-9]\\d{9}", message = "must be a 10-digit Indian mobile number without +91")
		String phone,

		// BCrypt only uses the first 72 bytes, so longer passwords are refused rather than silently cut.
		@NotBlank
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
				message = "must be 8 to 72 characters with at least one letter and one digit")
		String password,

		@NotNull @Past LocalDate dateOfBirth,

		@Size(max = 20) String gender,

		@NotBlank @Size(max = 255) String addressLine,

		@NotBlank @Size(max = 100) String city,

		@NotBlank @Size(max = 100) String state,

		@NotBlank @Pattern(regexp = "[1-9]\\d{5}", message = "must be a 6-digit PIN code") String pincode) {
}
