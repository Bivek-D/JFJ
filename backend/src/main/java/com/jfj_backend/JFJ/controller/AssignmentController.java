package com.jfj_backend.JFJ.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.jfj_backend.JFJ.config.OpenApiConfig;
import com.jfj_backend.JFJ.dto.response.AssignmentResponse;
import com.jfj_backend.JFJ.dto.response.ContactResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.security.WorkerOnly;
import com.jfj_backend.JFJ.service.AssignmentService;

@RestController
@RequestMapping("/api/v1/assignments")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AssignmentController {

	private final AssignmentService assignmentService;

	public AssignmentController(AssignmentService assignmentService) {
		this.assignmentService = assignmentService;
	}

	@WorkerOnly
	@GetMapping("/mine")
	public PageResponse<AssignmentResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return assignmentService.listMine(UUID.fromString(jwt.getSubject()), page, size);
	}

	@GetMapping("/{id}/contact")
	public ContactResponse contact(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return assignmentService.contact(UUID.fromString(jwt.getSubject()), id);
	}
}
