package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A worker on this gig the provider hasn't rated yet, and until when they still can (14 days). */
public record PendingReviewResponse(UUID assignmentId, UUID workerId, String workerName, Instant reviewOpenUntil) {
}
