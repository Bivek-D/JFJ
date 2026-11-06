package com.jfj_backend.JFJ.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.GigMapParams;
import com.jfj_backend.JFJ.dto.request.GigSearchParams;
import com.jfj_backend.JFJ.dto.response.GigSummaryResponse;
import com.jfj_backend.JFJ.dto.response.MapResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.service.GigSearchService;

import jakarta.validation.Valid;

/**
 * Finding gigs: list and map. Any logged-in user, any mode (a provider may look around too).
 * @ParameterObject lists the record's fields as query parameters in Swagger.
 * @Valid on a query-param record: a bad value (lat=abc, radiusKm=500) -> 400 with fieldErrors.
 */
@RestController
@RequestMapping("/api/v1/gigs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class GigSearchController {

	private final GigSearchService gigSearchService;

	public GigSearchController(GigSearchService gigSearchService) {
		this.gigSearchService = gigSearchService;
	}

	/** Open gigs: by distance when lat/lng are sent (radiusKm default 10, max 50), else newest first. */
	@GetMapping
	public PageResponse<GigSummaryResponse> list(@ParameterObject @Valid GigSearchParams params) {
		return gigSearchService.list(params);
	}

	/** Pins inside the visible map area, max 200 (truncated = true when there are more). */
	@GetMapping("/map")
	public MapResponse map(@ParameterObject @Valid GigMapParams params) {
		return gigSearchService.map(params);
	}
}
