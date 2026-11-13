package com.jfj_backend.JFJ.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import com.jfj_backend.JFJ.dto.request.ReviewRequest;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.dto.response.PendingReviewResponse;
import com.jfj_backend.JFJ.dto.response.ReviewResponse;
import com.jfj_backend.JFJ.security.ProviderOnly;
import com.jfj_backend.JFJ.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ReviewController {

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@PostMapping("/assignments/{id}/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ReviewResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody ReviewRequest request) {
		return reviewService.create(userId(jwt), id, request.rating(), request.comment());
	}

	@PutMapping("/reviews/{id}")
	public ReviewResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
			@Valid @RequestBody ReviewRequest request) {
		return reviewService.edit(userId(jwt), id, request.rating(), request.comment());
	}

	@GetMapping("/users/{id}/reviews")
	public PageResponse<ReviewResponse> forUser(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return reviewService.listFor(id, page, size);
	}

	@ProviderOnly
	@GetMapping("/gigs/{id}/reviews/pending")
	public List<PendingReviewResponse> pending(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return reviewService.pendingForGig(userId(jwt), id);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
