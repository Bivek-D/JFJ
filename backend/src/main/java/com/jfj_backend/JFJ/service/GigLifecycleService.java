package com.jfj_backend.JFJ.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.OwnerGigResponse;
import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.ApplicationRepository;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.GigRepository;

/**
 * Start, complete, cancel (PRD 4.4 lifecycle table). Each one locks the gig row first, like hiring,
 * so a hire can't slip in halfway through a complete or cancel: they take turns.
 * Everything in one method happens in one transaction: all of it, or none of it.
 */
@Service
public class GigLifecycleService {

	static final String CANCELLED_REASON = "gig cancelled";

	private final GigRepository gigs;
	private final AssignmentRepository assignments;
	private final ApplicationRepository applications;

	public GigLifecycleService(GigRepository gigs, AssignmentRepository assignments,
			ApplicationRepository applications) {
		this.gigs = gigs;
		this.assignments = assignments;
		this.applications = applications;
	}

	/** OPEN -> IN_PROGRESS; needs at least one hired worker. Not yet: GigStartJob does this on the start date. */
	@Transactional
	public OwnerGigResponse start(UUID providerId, UUID gigId) {
		Gig gig = lockOwn(providerId, gigId);
		if (activeAssignments(gigId).isEmpty()) {
			throw new BadRequestException("Hire at least one worker before starting the gig");
		}
		gig.start();
		return OwnerGigResponse.from(gig);
	}

	/**
	 * IN_PROGRESS -> COMPLETED (DONE-1): every ACTIVE assignment becomes COMPLETED with the same end
	 * time, and waiting applications are rejected. Not yet: completion XP (B5) and notifications.
	 */
	@Transactional
	public OwnerGigResponse complete(UUID providerId, UUID gigId) {
		Gig gig = lockOwn(providerId, gigId);
		List<Assignment> active = activeAssignments(gigId);
		if (active.isEmpty()) {
			throw new BadRequestException("A gig needs at least one active worker to be completed");
		}
		Instant now = Instant.now();
		gig.complete(now);
		active.forEach(assignment -> assignment.complete(now));
		rejectPending(gigId);
		return OwnerGigResponse.from(gig);
	}

	/**
	 * OPEN or IN_PROGRESS -> CANCELLED (GIG-3): hired workers are REMOVED with reason "gig cancelled"
	 * (no completion XP), waiting applications are rejected. Both sides may still review (B5).
	 */
	@Transactional
	public OwnerGigResponse cancel(UUID providerId, UUID gigId, String reason) {
		Gig gig = lockOwn(providerId, gigId);
		Instant now = Instant.now();
		gig.cancel(reason.trim());
		activeAssignments(gigId).forEach(assignment -> assignment.remove(CANCELLED_REASON, now));
		rejectPending(gigId);
		return OwnerGigResponse.from(gig);
	}

	/** PRD "Pending applications": on CANCELLED, EXPIRED or COMPLETED, APPLIED and SHORTLISTED become REJECTED. */
	private void rejectPending(UUID gigId) {
		applications.findByGigIdAndStatusIn(gigId, List.of(ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED))
				.forEach(Application::reject);
	}

	private List<Assignment> activeAssignments(UUID gigId) {
		return assignments.findByGigIdAndStatus(gigId, AssignmentStatus.ACTIVE);
	}

	private Gig lockOwn(UUID providerId, UUID gigId) {
		Gig gig = gigs.findByIdForUpdate(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		if (!gig.isOwnedBy(providerId)) {
			throw new ForbiddenException("You can only change your own gigs");
		}
		return gig;
	}
}
