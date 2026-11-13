package com.jfj_backend.JFJ.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.dto.response.PendingReviewResponse;
import com.jfj_backend.JFJ.dto.response.ReviewResponse;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.Review;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.enums.ReviewDirection;
import com.jfj_backend.JFJ.enums.XpEventType;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.DuplicateResourceException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.ReviewRepository;

/**
 * Create and edit run in ONE transaction: lock the reviewee's user row, update the rating sum/count,
 * write XP, update the level. So two reviews arriving together for the same person take turns and
 * neither update is lost.
 */
@Service
public class ReviewService {

	public static final Duration REVIEW_WINDOW = Duration.ofDays(14);
	public static final Duration EDIT_WINDOW = Duration.ofHours(24);

	private final ReviewRepository reviews;
	private final AssignmentRepository assignments;
	private final GigRepository gigs;
	private final XpService xpService;
	private final UserService userService;

	public ReviewService(ReviewRepository reviews, AssignmentRepository assignments, GigRepository gigs,
			XpService xpService, UserService userService) {
		this.reviews = reviews;
		this.assignments = assignments;
		this.gigs = gigs;
		this.xpService = xpService;
		this.userService = userService;
	}

	/** The direction comes from who is calling, never from the request. */
	@Transactional
	public ReviewResponse create(UUID callerId, UUID assignmentId, int rating, String comment) {
		Assignment assignment = assignments.findById(assignmentId)
				.orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
		UUID workerId = assignment.getWorker().getId();
		UUID providerId = assignment.getGig().getProvider().getId();
		ReviewDirection direction;
		UUID revieweeId;
		if (callerId.equals(providerId)) {
			direction = ReviewDirection.PROVIDER_TO_WORKER;
			revieweeId = workerId;
		}
		else if (callerId.equals(workerId)) {
			direction = ReviewDirection.WORKER_TO_PROVIDER;
			revieweeId = providerId;
		}
		else {
			throw new ResourceNotFoundException("Assignment not found");
		}
		if (assignment.getStatus() == AssignmentStatus.ACTIVE) {
			throw new BadRequestException("You can review once the job has ended");
		}
		Instant reviewOpenUntil = assignment.getEndedAt().plus(REVIEW_WINDOW);
		if (Instant.now().isAfter(reviewOpenUntil)) {
			throw new BadRequestException("Reviews are only possible for 14 days after the job ends");
		}
		if (reviews.existsByAssignmentIdAndReviewerId(assignmentId, callerId)) {
			throw new DuplicateResourceException("You have already reviewed this job");
		}

		User reviewee = xpService.lock(revieweeId);
		Review review;
		try {
			review = reviews.saveAndFlush(new Review(assignment, userService.getById(callerId), reviewee, direction,
					rating, clean(comment)));
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateResourceException("You have already reviewed this job");
		}
		addRating(reviewee, direction, rating, 1);
		xpService.award(reviewee, XpEventType.RATING_RECEIVED, review.getId(), 0, XpService.forRating(rating));
		return ReviewResponse.from(review, review.getCreatedAt().plus(EDIT_WINDOW));
	}

	/** The aggregate and XP move by the difference only. */
	@Transactional
	public ReviewResponse edit(UUID callerId, UUID reviewId, int rating, String comment) {
		Review review = reviews.findById(reviewId)
				.filter(r -> r.getReviewer().getId().equals(callerId))
				.orElseThrow(() -> new ResourceNotFoundException("Review not found"));
		Instant editableUntil = review.getCreatedAt().plus(EDIT_WINDOW);
		if (Instant.now().isAfter(editableUntil)) {
			throw new ForbiddenException("Reviews can only be changed for 24 hours");
		}
		User reviewee = xpService.lock(review.getReviewee().getId());
		int oldRating = review.edit(rating, clean(comment));
		addRating(reviewee, review.getDirection(), rating - oldRating, 0);
		int xpChange = XpService.forRating(rating) - XpService.forRating(oldRating);
		if (xpChange != 0) {
			// version = edit_count, so every edit is its own event and none is applied twice.
			xpService.award(reviewee, XpEventType.RATING_ADJUST, review.getId(), review.getEditCount(), xpChange);
		}
		return ReviewResponse.from(review, editableUntil);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReviewResponse> listFor(UUID userId, int page, int size) {
		userService.getById(userId);
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, GigService.MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		return PageResponse.from(reviews.findByRevieweeId(userId, request), r -> ReviewResponse.from(r, null));
	}

	@Transactional(readOnly = true)
	public List<PendingReviewResponse> pendingForGig(UUID providerId, UUID gigId) {
		Gig gig = gigs.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		if (!gig.isOwnedBy(providerId)) {
			throw new ForbiddenException("You can only see reviews for your own gigs");
		}
		List<Assignment> ended = assignments.findByGigIdAndStatusIn(gigId,
				List.of(AssignmentStatus.COMPLETED, AssignmentStatus.REMOVED));
		Set<UUID> reviewed = reviews.findByAssignmentIdIn(ended.stream().map(Assignment::getId).toList()).stream()
				.filter(r -> r.getReviewer().getId().equals(providerId))
				.map(r -> r.getAssignment().getId())
				.collect(Collectors.toSet());
		Instant now = Instant.now();
		return ended.stream()
				.filter(a -> !reviewed.contains(a.getId()))
				.filter(a -> !now.isAfter(a.getEndedAt().plus(REVIEW_WINDOW)))
				.map(a -> new PendingReviewResponse(a.getId(), a.getWorker().getId(), a.getWorker().getName(),
						a.getEndedAt().plus(REVIEW_WINDOW)))
				.toList();
	}

	private static void addRating(User reviewee, ReviewDirection direction, int sumDelta, int countDelta) {
		if (direction == ReviewDirection.PROVIDER_TO_WORKER) {
			reviewee.addWorkerRating(sumDelta, countDelta);
		}
		else {
			reviewee.addProviderRating(sumDelta, countDelta);
		}
	}

	private static String clean(String comment) {
		return (comment == null || comment.isBlank()) ? null : comment.trim();
	}
}
