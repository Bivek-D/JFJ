package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.enums.PayType;

public record ApplicationResponse(UUID id, ApplicationStatus status, String note, Instant createdAt, GigBrief gig,
		AssignmentBrief assignment) {

	public record GigBrief(UUID id, String title, GigStatus status, LocalDate startDate, PayType payType,
			BigDecimal payAmount, String locality, String city) {
	}

	public record AssignmentBrief(UUID id, AssignmentStatus status, String removedReason, Instant endedAt) {
	}

	public static ApplicationResponse from(Application application, Assignment assignment) {
		Gig gig = application.getGig();
		return new ApplicationResponse(application.getId(), application.getStatus(), application.getNote(),
				application.getCreatedAt(),
				new GigBrief(gig.getId(), gig.getTitle(), gig.getStatus(), gig.getStartDate(), gig.getPayType(),
						gig.getPayAmount(), gig.getLocality(), gig.getCity()),
				assignment == null ? null
						: new AssignmentBrief(assignment.getId(), assignment.getStatus(),
								assignment.getRemovedReason(), assignment.getEndedAt()));
	}
}
