package com.jfj_backend.JFJ.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.AssignmentResponse;
import com.jfj_backend.JFJ.dto.response.ContactResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.ReviewRepository;

/** A worker's jobs, and the contact reveal between the two parties of a job (APP-6, APP-7). */
@Service
public class AssignmentService {

	public static final Duration CONTACT_WINDOW = Duration.ofDays(7);

	private final AssignmentRepository assignments;
	private final ReviewRepository reviews;

	public AssignmentService(AssignmentRepository assignments, ReviewRepository reviews) {
		this.assignments = assignments;
		this.reviews = reviews;
	}

	/** GET /assignments/mine: newest hire first. */
	@Transactional(readOnly = true)
	public PageResponse<AssignmentResponse> listMine(UUID workerId, int page, int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, GigService.MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		var result = assignments.findByWorkerId(workerId, request);
		java.util.Set<UUID> reviewed = reviews.findByAssignmentIdIn(result.getContent().stream()
				.map(Assignment::getId).toList()).stream()
				.filter(r -> r.getReviewer().getId().equals(workerId))
				.map(r -> r.getAssignment().getId())
				.collect(java.util.stream.Collectors.toSet());
		return PageResponse.from(result, a -> AssignmentResponse.from(a, reviewed.contains(a.getId()),
				a.getEndedAt() == null ? null : a.getEndedAt().plus(ReviewService.REVIEW_WINDOW)));
	}

	/**
	 * The other party's name and phone (APP-7), only for the worker or the gig's owner:
	 * - ACTIVE: both sides.
	 * - COMPLETED: both sides, for 7 days after it ended.
	 * - REMOVED: the worker loses access at once; the provider keeps it for 7 days.
	 * Anyone else gets 404, so assignment ids can't be probed.
	 */
	@Transactional(readOnly = true)
	public ContactResponse contact(UUID callerId, UUID assignmentId) {
		Assignment assignment = assignments.findById(assignmentId)
				.orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
		User worker = assignment.getWorker();
		User provider = assignment.getGig().getProvider();
		boolean callerIsWorker = worker.getId().equals(callerId);
		boolean callerIsProvider = provider.getId().equals(callerId);
		if (!callerIsWorker && !callerIsProvider) {
			throw new ResourceNotFoundException("Assignment not found");
		}

		Instant availableUntil = null;
		if (assignment.getStatus() != AssignmentStatus.ACTIVE) {
			if (assignment.getStatus() == AssignmentStatus.REMOVED && callerIsWorker) {
				throw new ForbiddenException("You no longer have access to this contact");
			}
			availableUntil = assignment.getEndedAt().plus(CONTACT_WINDOW);
			if (Instant.now().isAfter(availableUntil)) {
				throw new ForbiddenException("Contact details are only shown for 7 days after the job ends");
			}
		}

		User other = callerIsWorker ? provider : worker;
		return new ContactResponse(callerIsWorker ? "PROVIDER" : "WORKER", other.getName(), other.getPhone(),
				other.isPhoneVerified(), availableUntil);
	}
}
