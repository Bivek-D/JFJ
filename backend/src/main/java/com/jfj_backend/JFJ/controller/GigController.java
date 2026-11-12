package com.jfj_backend.JFJ.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.CancelGigRequest;
import com.jfj_backend.JFJ.dto.request.GigRequest;
import com.jfj_backend.JFJ.dto.response.GigDetailResponse;
import com.jfj_backend.JFJ.dto.response.OwnerGigResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.security.ProviderOnly;
import com.jfj_backend.JFJ.service.GigLifecycleService;
import com.jfj_backend.JFJ.service.GigService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/gigs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class GigController {

	private final GigService gigService;
	private final GigLifecycleService gigLifecycleService;

	public GigController(GigService gigService, GigLifecycleService gigLifecycleService) {
		this.gigService = gigService;
		this.gigLifecycleService = gigLifecycleService;
	}

	@ProviderOnly
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OwnerGigResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GigRequest request) {
		return gigService.create(userId(jwt), request);
	}

	@ProviderOnly
	@PutMapping("/{id}")
	public OwnerGigResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody GigRequest request) {
		return gigService.update(userId(jwt), id, request);
	}

	@ProviderOnly
	@PostMapping("/{id}/publish")
	public OwnerGigResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return gigService.publish(userId(jwt), id);
	}

	@ProviderOnly
	@PostMapping("/{id}/start")
	public OwnerGigResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return gigLifecycleService.start(userId(jwt), id);
	}

	@ProviderOnly
	@PostMapping("/{id}/complete")
	public OwnerGigResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return gigLifecycleService.complete(userId(jwt), id);
	}

	@ProviderOnly
	@PostMapping("/{id}/cancel")
	public OwnerGigResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody CancelGigRequest request) {
		return gigLifecycleService.cancel(userId(jwt), id, request.reason());
	}

	@ProviderOnly
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		gigService.delete(userId(jwt), id);
	}

	@GetMapping("/{id}")
	public GigDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return gigService.getDetail(userId(jwt), id);
	}

	@ProviderOnly
	@GetMapping("/mine")
	public PageResponse<OwnerGigResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) GigStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return gigService.listMine(userId(jwt), status, page, size);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
