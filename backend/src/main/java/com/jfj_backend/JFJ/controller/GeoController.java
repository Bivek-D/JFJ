package com.jfj_backend.JFJ.controller;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.AutocompleteParams;
import com.jfj_backend.JFJ.dto.request.ReverseParams;
import com.jfj_backend.JFJ.dto.response.AddressResponse;
import com.jfj_backend.JFJ.service.GeoService;

import jakarta.validation.Valid;

/**
 * Address search proxy (PRD 6.4). Login required (the rate limit is per user).
 * 429 = too many calls this minute; 503 = Geoapify unavailable. Either way the pin + typed address works.
 */
@RestController
@RequestMapping("/api/v1/geo")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class GeoController {

	private final GeoService geoService;

	public GeoController(GeoService geoService) {
		this.geoService = geoService;
	}

	@GetMapping("/autocomplete")
	public List<AddressResponse> autocomplete(@AuthenticationPrincipal Jwt jwt,
			@ParameterObject @Valid AutocompleteParams params) {
		return geoService.autocomplete(UUID.fromString(jwt.getSubject()), params.q());
	}

	@GetMapping("/reverse")
	public AddressResponse reverse(@AuthenticationPrincipal Jwt jwt, @ParameterObject @Valid ReverseParams params) {
		return geoService.reverse(UUID.fromString(jwt.getSubject()), params.lat(), params.lng());
	}
}
