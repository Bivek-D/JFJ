package com.jfj_backend.JFJ.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.request.UpdateWorkerProfileRequest;
import com.jfj_backend.JFJ.dto.request.UpdateWorkerSkillsRequest;
import com.jfj_backend.JFJ.dto.response.PublicWorkerProfileResponse;
import com.jfj_backend.JFJ.dto.response.WorkerProfileResponse;
import com.jfj_backend.JFJ.security.WorkerOnly;
import com.jfj_backend.JFJ.service.WorkerProfileService;

import jakarta.validation.Valid;

/**
 * Reading works in any mode (a provider can check their own worker profile too);
 * editing needs Worker mode, like every worker action (PRD 4.1).
 * "me" is always taken from the token, never from the URL, so nobody can edit someone else's profile.
 */
@RestController
@RequestMapping("/api/v1/workers")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class WorkerProfileController {

	private final WorkerProfileService workerProfileService;

	public WorkerProfileController(WorkerProfileService workerProfileService) {
		this.workerProfileService = workerProfileService;
	}

	@GetMapping("/me/profile")
	public WorkerProfileResponse getOwn(@AuthenticationPrincipal Jwt jwt) {
		return workerProfileService.getOwn(userId(jwt));
	}

	@WorkerOnly
	@PutMapping("/me/profile")
	public WorkerProfileResponse updateOwn(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateWorkerProfileRequest request) {
		return workerProfileService.updateOwn(userId(jwt), request);
	}

	@WorkerOnly
	@PutMapping("/me/skills")
	public WorkerProfileResponse replaceSkills(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UpdateWorkerSkillsRequest request) {
		return workerProfileService.replaceSkills(userId(jwt), request.skillIds());
	}

	@GetMapping("/{userId}")
	public PublicWorkerProfileResponse getPublic(@PathVariable UUID userId) {
		return workerProfileService.getPublic(userId);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
