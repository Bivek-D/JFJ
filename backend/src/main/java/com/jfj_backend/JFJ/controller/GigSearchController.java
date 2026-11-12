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

@RestController
@RequestMapping("/api/v1/gigs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class GigSearchController {

	private final GigSearchService gigSearchService;

	public GigSearchController(GigSearchService gigSearchService) {
		this.gigSearchService = gigSearchService;
	}

	@GetMapping
	public PageResponse<GigSummaryResponse> list(@ParameterObject @Valid GigSearchParams params) {
		return gigSearchService.list(params);
	}

	@GetMapping("/map")
	public MapResponse map(@ParameterObject @Valid GigMapParams params) {
		return gigSearchService.map(params);
	}
}
