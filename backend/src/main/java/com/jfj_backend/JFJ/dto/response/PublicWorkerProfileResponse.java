package com.jfj_backend.JFJ.dto.response;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.ExperienceLevel;

/** Deliberately has no email, phone, address, pincode or date of birth. City is enough to judge distance. */
public record PublicWorkerProfileResponse(UUID userId, String name, String city, String photoUrl,
		String description, List<String> languages, int experienceYears, ExperienceLevel experienceLevel,
		List<SkillResponse> skills, boolean profileComplete, RatingSummary rating, LevelInfo level,
		long completedGigs) {

	public static PublicWorkerProfileResponse from(WorkerProfile profile, LevelInfo level, long completedGigs) {
		List<SkillResponse> skills = profile.getSkills().stream()
				.map(SkillResponse::from)
				.sorted(Comparator.comparing(SkillResponse::name))
				.toList();
		return new PublicWorkerProfileResponse(profile.getUserId(), profile.getUser().getName(),
				profile.getUser().getCity(), profile.getPhotoUrl(), profile.getDescription(), profile.getLanguages(),
				profile.getExperienceYears(), profile.getExperienceLevel(), skills, profile.isProfileComplete(),
				RatingSummary.of(profile.getUser().getRatingSum(), profile.getUser().getRatingCount()), level,
				completedGigs);
	}
}
