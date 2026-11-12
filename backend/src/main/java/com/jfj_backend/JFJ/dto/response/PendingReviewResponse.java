package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PendingReviewResponse(UUID assignmentId, UUID workerId, String workerName, Instant reviewOpenUntil) {
}
