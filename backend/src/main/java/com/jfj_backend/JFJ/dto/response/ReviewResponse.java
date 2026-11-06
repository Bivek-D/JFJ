package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.Review;
import com.jfj_backend.JFJ.enums.ReviewDirection;

/** A review, shown on profiles (GET /users/{id}/reviews) and returned after create/edit. */
public record ReviewResponse(UUID id, UUID assignmentId, ReviewDirection direction, int rating, String comment,
		UUID reviewerId, String reviewerName, int editCount, Instant createdAt, Instant editableUntil) {

	public static ReviewResponse from(Review review, Instant editableUntil) {
		return new ReviewResponse(review.getId(), review.getAssignment().getId(), review.getDirection(),
				review.getRating(), review.getComment(), review.getReviewer().getId(), review.getReviewer().getName(),
				review.getEditCount(), review.getCreatedAt(), editableUntil);
	}
}
