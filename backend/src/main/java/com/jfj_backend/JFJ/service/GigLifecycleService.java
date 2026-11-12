package com.jfj_backend.JFJ.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.OwnerGigResponse;
import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.enums.XpEventType;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.ApplicationRepository;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.GigRepository;

/**
 * Each action locks the gig row first, like hiring, so a hire can't slip in halfway through a
 * complete or cancel: they take turns.
 */
@Service
public class GigLifecycleService {

	static final String CANCELLED_REASON = "gig cancelled";

	private final GigRepository gigs;
	private final AssignmentRepository assignments;
	private final ApplicationRepository applications;
	private final XpService xpService;

	public GigLifecycleService(GigRepository gigs, AssignmentRepository assignments,
			ApplicationRepository applications, XpService xpService) {
		this.gigs = gigs;
		this.assignments = assignments;
		this.applications = applications;
		this.xpService = xpService;
	}

	@Transactional
	public OwnerGigResponse start(UUID providerId, UUID gigId) {
		Gig gig = lockOwn(providerId, gigId);
		if (activeAssignments(gigId).isEmpty()) {
			throw new BadRequestException("Hire at least one worker before starting the gig");
		}
		gig.start();
		return OwnerGigResponse.from(gig);
	}

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

		// Completion XP in the same transaction: +20 per worker, +10 once for the provider.
		// All users are locked first, in one fixed order (lockAll), to avoid deadlocks.
		List<UUID> userIds = new java.util.ArrayList<>(active.stream().map(a -> a.getWorker().getId()).toList());
		userIds.add(providerId);
		Map<UUID, User> locked = xpService.lockAll(userIds);
		for (Assignment assignment : active) {
			xpService.award(locked.get(assignment.getWorker().getId()), XpEventType.GIG_COMPLETED,
					assignment.getId(), 0, XpService.GIG_COMPLETED_XP);
		}
		xpService.award(locked.get(providerId), XpEventType.GIG_COMPLETED_PROVIDER, gigId, 0,
				XpService.GIG_COMPLETED_PROVIDER_XP);
		return OwnerGigResponse.from(gig);
	}

	@Transactional
	public OwnerGigResponse cancel(UUID providerId, UUID gigId, String reason) {
		Gig gig = lockOwn(providerId, gigId);
		Instant now = Instant.now();
		gig.cancel(reason.trim());
		activeAssignments(gigId).forEach(assignment -> assignment.remove(CANCELLED_REASON, now));
		rejectPending(gigId);
		return OwnerGigResponse.from(gig);
	}

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
