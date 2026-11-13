package com.jfj_backend.JFJ.dto.response;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.enums.ExperienceLevel;

/** No phone, email or address: the phone is revealed only after hiring. */
public record ApplicantResponse(UUID id, ApplicationStatus status, String note, Instant createdAt,
		ApplicantWorker worker, UUID assignmentId) {

	public record ApplicantWorker(UUID id, String name, String city, String photoUrl, ExperienceLevel experienceLevel,
			int experienceYears, List<String> languages, List<SkillResponse> skills, RatingSummary rating,
			LevelInfo level) {
	}

	public static ApplicantResponse from(Application application, WorkerProfile profile, UUID assignmentId,
			LevelInfo level) {
		List<SkillResponse> skills = profile == null ? List.of()
				: profile.getSkills().stream().map(SkillResponse::from)
						.sorted(Comparator.comparing(SkillResponse::name)).toList();
		ApplicantWorker worker = new ApplicantWorker(application.getWorker().getId(),
				application.getWorker().getName(), application.getWorker().getCity(),
				profile == null ? null : profile.getPhotoUrl(),
				profile == null ? null : profile.getExperienceLevel(),
				profile == null ? 0 : profile.getExperienceYears(),
				profile == null ? List.of() : profile.getLanguages(), skills,
				RatingSummary.of(application.getWorker().getRatingSum(), application.getWorker().getRatingCount()),
				level);
		return new ApplicantResponse(application.getId(), application.getStatus(), application.getNote(),
				application.getCreatedAt(), worker, assignmentId);
	}
}
