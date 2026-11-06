package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.AssignmentStatus;

/**
 * GET /assignments/mine (APP-6): what the worker's "My jobs" screen needs, incl. the assignment id for
 * contact and (Day 11) reviews. cancelReason is filled when the gig was cancelled.
 * reviewedByMe: the worker has rated the provider; reviewOpenUntil: last moment to do so (null while active).
 */
public record AssignmentResponse(UUID id, AssignmentStatus status, String removedReason, Instant endedAt,
		Instant hiredAt, ApplicationResponse.GigBrief gig, String cancelReason, boolean reviewedByMe,
		Instant reviewOpenUntil) {

	public static AssignmentResponse from(Assignment assignment, boolean reviewedByMe, Instant reviewOpenUntil) {
		Gig gig = assignment.getGig();
		return new AssignmentResponse(assignment.getId(), assignment.getStatus(), assignment.getRemovedReason(),
				assignment.getEndedAt(), assignment.getCreatedAt(),
				new ApplicationResponse.GigBrief(gig.getId(), gig.getTitle(), gig.getStatus(), gig.getStartDate(),
						gig.getPayType(), gig.getPayAmount(), gig.getLocality(), gig.getCity()),
				gig.getCancelReason(), reviewedByMe, reviewOpenUntil);
	}
}
