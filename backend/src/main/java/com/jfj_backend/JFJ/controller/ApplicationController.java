package com.jfj_backend.JFJ.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.ApplyRequest;
import com.jfj_backend.JFJ.dto.response.ApplicantResponse;
import com.jfj_backend.JFJ.dto.response.ApplicationResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.security.ProviderOnly;
import com.jfj_backend.JFJ.security.WorkerOnly;
import com.jfj_backend.JFJ.service.ApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@WorkerOnly
	@PostMapping("/gigs/{gigId}/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationResponse apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gigId,
			@Valid @RequestBody(required = false) ApplyRequest request) {
		return applicationService.apply(userId(jwt), gigId, request == null ? null : request.note());
	}

	@WorkerOnly
	@GetMapping("/applications/mine")
	public PageResponse<ApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return applicationService.listMine(userId(jwt), page, size);
	}

	@WorkerOnly
	@DeleteMapping("/applications/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		applicationService.withdraw(userId(jwt), id);
	}

	@ProviderOnly
	@GetMapping("/gigs/{gigId}/applications")
	public List<ApplicantResponse> listForGig(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID gigId,
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) ApplicationService.ApplicantSort sort) {
		return applicationService.listForGig(userId(jwt), gigId, status, sort);
	}

	@ProviderOnly
	@PostMapping("/applications/{id}/shortlist")
	public ApplicantResponse shortlist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return applicationService.shortlist(userId(jwt), id);
	}

	@ProviderOnly
	@PostMapping("/applications/{id}/reject")
	public ApplicantResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return applicationService.reject(userId(jwt), id);
	}

	@ProviderOnly
	@PostMapping("/applications/{id}/hire")
	public ApplicantResponse hire(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return applicationService.hire(userId(jwt), id);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
