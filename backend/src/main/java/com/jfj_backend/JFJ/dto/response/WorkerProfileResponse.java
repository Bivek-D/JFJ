package com.jfj_backend.JFJ.dto.response;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.ExperienceLevel;

public record WorkerProfileResponse(UUID userId, String name, String photoUrl, String description,
		List<String> languages, int experienceYears, ExperienceLevel experienceLevel, List<SkillResponse> skills,
		boolean profileComplete, List<String> missing) {

	public static WorkerProfileResponse from(WorkerProfile profile) {
		List<SkillResponse> skills = profile.getSkills().stream()
				.map(SkillResponse::from)
				.sorted(Comparator.comparing(SkillResponse::name))
				.toList();
		return new WorkerProfileResponse(profile.getUserId(), profile.getUser().getName(), profile.getPhotoUrl(),
				profile.getDescription(), profile.getLanguages(), profile.getExperienceYears(),
				profile.getExperienceLevel(), skills, profile.isProfileComplete(),
				missing(profile.getPhotoUrl(), skills.isEmpty(), profile.getLanguages().isEmpty()));
	}

	public static WorkerProfileResponse empty(User user) {
		return new WorkerProfileResponse(user.getId(), user.getName(), null, null, List.of(), 0,
				ExperienceLevel.BEGINNER, List.of(), false, missing(null, true, true));
	}

	private static List<String> missing(String photoUrl, boolean noSkills, boolean noLanguages) {
		List<String> missing = new ArrayList<>();
		if (photoUrl == null) {
			missing.add("photo");
		}
		if (noSkills) {
			missing.add("skill");
		}
		if (noLanguages) {
			missing.add("language");
		}
		return missing;
	}
}
