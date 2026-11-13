package com.jfj_backend.JFJ.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.request.UpdateWorkerProfileRequest;
import com.jfj_backend.JFJ.dto.response.PublicWorkerProfileResponse;
import com.jfj_backend.JFJ.dto.response.WorkerProfileResponse;
import com.jfj_backend.JFJ.entity.Skill;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.enums.XpEventType;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.WorkerProfileRepository;

/** Responses are built inside the transaction, so lazy fields (user, skills) can still be loaded. */
@Service
public class WorkerProfileService {

	private final WorkerProfileRepository profiles;
	private final CatalogService catalogService;
	private final UserService userService;
	private final XpService xpService;
	private final AssignmentRepository assignments;

	public WorkerProfileService(WorkerProfileRepository profiles, CatalogService catalogService,
			UserService userService, XpService xpService, AssignmentRepository assignments) {
		this.profiles = profiles;
		this.catalogService = catalogService;
		this.userService = userService;
		this.xpService = xpService;
		this.assignments = assignments;
	}

	@Transactional(readOnly = true)
	public WorkerProfileResponse getOwn(UUID userId) {
		return profiles.findById(userId)
				.map(WorkerProfileResponse::from)
				.orElseGet(() -> WorkerProfileResponse.empty(userService.getById(userId)));
	}

	@Transactional
	public WorkerProfileResponse updateOwn(UUID userId, UpdateWorkerProfileRequest request) {
		WorkerProfile profile = getOrCreate(userId);
		profile.updateDetails(blankToNull(request.photoUrl()), blankToNull(request.description()),
				cleanLanguages(request.languages()), request.experienceYears(), request.experienceLevel());
		awardIfComplete(profile);
		return WorkerProfileResponse.from(profile);
	}

	@Transactional
	public WorkerProfileResponse replaceSkills(UUID userId, Set<Long> skillIds) {
		Set<Skill> found = catalogService.requireSkills(skillIds);
		WorkerProfile profile = getOrCreate(userId);
		profile.replaceSkills(found);
		awardIfComplete(profile);
		return WorkerProfileResponse.from(profile);
	}

	/** Missing, suspended and deleted profiles all give the same 404, so they can't be told apart. */
	@Transactional(readOnly = true)
	public PublicWorkerProfileResponse getPublic(UUID userId) {
		return profiles.findById(userId)
				.filter(profile -> profile.getUser().getStatus() == UserStatus.ACTIVE)
				.map(profile -> PublicWorkerProfileResponse.from(profile, xpService.levelInfo(profile.getUser()),
						assignments.countByWorkerIdAndStatus(userId, AssignmentStatus.COMPLETED)))
				.orElseThrow(() -> new ResourceNotFoundException("Worker profile not found"));
	}

	// Two first saves at the same moment could both try to insert; the second fails on the primary key.
	// Rare (one person, one profile) and harmless, so it isn't handled.
	private WorkerProfile getOrCreate(UUID userId) {
		return profiles.findById(userId).orElseGet(() -> {
			User user = userService.getById(userId);
			return profiles.save(new WorkerProfile(user));
		});
	}

	private void awardIfComplete(WorkerProfile profile) {
		if (profile.isProfileComplete()) {
			xpService.award(xpService.lock(profile.getUserId()), XpEventType.PROFILE_COMPLETE, profile.getUserId(), 0,
					XpService.PROFILE_COMPLETE_XP);
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	private static List<String> cleanLanguages(List<String> languages) {
		Map<String, String> byLowercase = new LinkedHashMap<>();
		languages.stream().map(String::trim)
				.forEach(language -> byLowercase.putIfAbsent(language.toLowerCase(Locale.ROOT), language));
		return new ArrayList<>(byLowercase.values());
	}
}
