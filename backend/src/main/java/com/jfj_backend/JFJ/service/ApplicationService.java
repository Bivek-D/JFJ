package com.jfj_backend.JFJ.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.ApplicantResponse;
import com.jfj_backend.JFJ.dto.response.ApplicationResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.entity.WorkerProfile;
import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.enums.AssignmentStatus;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.DuplicateResourceException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.ApplicationRepository;
import com.jfj_backend.JFJ.repository.AssignmentRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.WorkerProfileRepository;
import com.jfj_backend.JFJ.util.AgeUtils;

/** Applying and hiring (PRD 5.5). Worker side: apply, withdraw, mine. Provider side: list, shortlist, reject, hire. */
@Service
public class ApplicationService {

	private final ApplicationRepository applications;
	private final AssignmentRepository assignments;
	private final GigRepository gigs;
	private final WorkerProfileRepository profiles;
	private final UserService userService;
	private final XpService xpService;

	public ApplicationService(ApplicationRepository applications, AssignmentRepository assignments,
			GigRepository gigs, WorkerProfileRepository profiles, UserService userService, XpService xpService) {
		this.xpService = xpService;
		this.applications = applications;
		this.assignments = assignments;
		this.gigs = gigs;
		this.profiles = profiles;
		this.userService = userService;
	}

	// ---------- worker ----------

	/** APP-1 and the lifecycle table: every rule is checked here, in the order a worker would want to hear it. */
	@Transactional
	public ApplicationResponse apply(UUID workerId, UUID gigId, String note) {
		Gig gig = gigs.findById(gigId)
				.filter(g -> g.getStatus() == GigStatus.OPEN && g.getProvider().getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		if (gig.isOwnedBy(workerId)) {
			throw new BadRequestException("You can't apply to your own gig");
		}
		if (gig.getStartDate().isBefore(AgeUtils.todayInIndia())
				|| (gig.getApplyDeadline() != null && gig.getApplyDeadline().isBefore(AgeUtils.todayInIndia()))) {
			throw new BadRequestException("Applications for this gig are closed");
		}
		if (openingsLeft(gig) <= 0) {
			throw new BadRequestException("This gig has no openings left");
		}
		WorkerProfile profile = profiles.findById(workerId)
				.filter(WorkerProfile::isProfileComplete)
				.orElseThrow(() -> new BadRequestException(
						"Complete your profile first: a photo, at least one skill and one language"));
		if (gig.getMinExperience() != null
				&& profile.getExperienceLevel().ordinal() < gig.getMinExperience().ordinal()) {
			throw new BadRequestException("This gig needs " + gig.getMinExperience().name().toLowerCase()
					+ " experience or more");
		}
		User worker = userService.getById(workerId);
		if (gig.getMinLevel() != null && worker.getLevel() < gig.getMinLevel()) {
			throw new BadRequestException("This gig needs level " + gig.getMinLevel() + " ("
					+ xpService.levelName(gig.getMinLevel()) + ") or higher");
		}
		if (applications.existsByGigIdAndWorkerId(gigId, workerId)) {
			throw new DuplicateResourceException("You have already applied to this gig");
		}
		String cleanNote = (note == null || note.isBlank()) ? null : note.trim();
		try {
			// Flush now: if a double tap raced past the check above, the UNIQUE (gig_id, worker_id) fails here.
			Application saved = applications.saveAndFlush(new Application(gig, worker, cleanNote));
			return ApplicationResponse.from(saved, null);
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateResourceException("You have already applied to this gig");
		}
	}

	/** APP-2: only while APPLIED or SHORTLISTED. */
	@Transactional
	public void withdraw(UUID workerId, UUID applicationId) {
		Application application = applications.findById(applicationId)
				.filter(a -> a.isFrom(workerId))
				.orElseThrow(() -> new ResourceNotFoundException("Application not found"));
		application.withdraw();
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicationResponse> listMine(UUID workerId, int page, int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, GigService.MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		var result = applications.findByWorkerId(workerId, request);
		Map<UUID, Assignment> hires = assignmentsFor(result.getContent());
		return PageResponse.from(result, a -> ApplicationResponse.from(a, hires.get(a.getId())));
	}

	// ---------- provider ----------

	/** How the provider wants applicants ordered (APP-3). */
	public enum ApplicantSort {
		NEWEST, RATING, LEVEL
	}

	/**
	 * APP-3: owner only, optionally one status. RATING = best average first (unrated last), LEVEL = highest
	 * level first (then XP); ties stay newest first. Sorted in Java: a gig has tens of applicants, not thousands.
	 */
	@Transactional(readOnly = true)
	public List<ApplicantResponse> listForGig(UUID providerId, UUID gigId, ApplicationStatus status,
			ApplicantSort sort) {
		Gig gig = gigs.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		requireOwner(gig, providerId);
		List<Application> list = status == null
				? applications.findByGigIdOrderByCreatedAtDesc(gigId)
				: applications.findByGigIdAndStatusOrderByCreatedAtDesc(gigId, status);
		Map<UUID, Assignment> hires = assignmentsFor(list);
		// One profile lookup per applicant (an "N+1" pattern). Fine for tens of applicants; batch it if it grows.
		java.util.Comparator<ApplicantResponse> order = switch (sort == null ? ApplicantSort.NEWEST : sort) {
			case NEWEST -> (x, y) -> 0;
			case RATING -> java.util.Comparator.comparing(
					(ApplicantResponse r) -> r.worker().rating().average(),
					java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
			case LEVEL -> java.util.Comparator.comparing((ApplicantResponse r) -> r.worker().level().level())
					.thenComparing(r -> r.worker().level().totalXp()).reversed();
		};
		// The list already comes newest first; a stable sort keeps that order among equals.
		return list.stream()
				.map(a -> toApplicant(a, hires.containsKey(a.getId()) ? hires.get(a.getId()).getId() : null))
				.sorted(order)
				.toList();
	}

	@Transactional
	public ApplicantResponse shortlist(UUID providerId, UUID applicationId) {
		Application application = getForOwner(providerId, applicationId);
		application.shortlist();
		return toApplicant(application, null);
	}

	@Transactional
	public ApplicantResponse reject(UUID providerId, UUID applicationId) {
		Application application = getForOwner(providerId, applicationId);
		application.reject();
		return toApplicant(application, null);
	}

	/**
	 * APP-4: hire = create an assignment, but never more than workers_needed.
	 *
	 * The race: two hires at the same moment would both count "0 hired" and both succeed.
	 * The fix: lock the gig row FIRST (SELECT ... FOR UPDATE). Every hire for this gig now waits in
	 * line; the next one starts only after the previous commits, and then counts again.
	 * The application is loaded only AFTER the lock, so it is never a stale copy from before the wait.
	 */
	@Transactional
	public ApplicantResponse hire(UUID providerId, UUID applicationId) {
		UUID gigId = applications.findGigIdById(applicationId)
				.orElseThrow(() -> new ResourceNotFoundException("Application not found"));
		Gig gig = gigs.findByIdForUpdate(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		requireOwner(gig, providerId);
		if (gig.getStatus() != GigStatus.OPEN && gig.getStatus() != GigStatus.IN_PROGRESS) {
			throw new BadRequestException("Workers can only be hired while the gig is open or in progress");
		}
		Application application = applications.findById(applicationId)
				.orElseThrow(() -> new ResourceNotFoundException("Application not found"));
		if (application.getWorker().getStatus() != UserStatus.ACTIVE) {
			throw new BadRequestException("This worker's account is not active");
		}
		if (openingsLeft(gig) <= 0) {
			throw new BadRequestException("No openings left: all " + gig.getWorkersNeeded() + " places are filled");
		}
		application.markHired();
		Assignment assignment = assignments.save(new Assignment(application));
		return toApplicant(application, assignment.getId());
	}

	// ---------- shared ----------

	/** PRD: there is no FULL status; openings are always worked out from ACTIVE assignments. */
	public int openingsLeft(Gig gig) {
		return gig.getWorkersNeeded() - (int) assignments.countByGigIdAndStatus(gig.getId(), AssignmentStatus.ACTIVE);
	}

	private Application getForOwner(UUID providerId, UUID applicationId) {
		Application application = applications.findById(applicationId)
				.orElseThrow(() -> new ResourceNotFoundException("Application not found"));
		requireOwner(application.getGig(), providerId);
		return application;
	}

	private static void requireOwner(Gig gig, UUID providerId) {
		if (!gig.isOwnedBy(providerId)) {
			throw new ForbiddenException("You can only manage applications for your own gigs");
		}
	}

	private ApplicantResponse toApplicant(Application application, UUID assignmentId) {
		return ApplicantResponse.from(application, profiles.findById(application.getWorker().getId()).orElse(null),
				assignmentId, xpService.levelInfo(application.getWorker()));
	}

	private Map<UUID, Assignment> assignmentsFor(List<Application> list) {
		List<UUID> ids = list.stream().filter(a -> a.getStatus() == ApplicationStatus.HIRED).map(Application::getId)
				.toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return assignments.findByApplicationIdIn(ids).stream()
				.collect(Collectors.toMap(a -> a.getApplication().getId(), Function.identity()));
	}
}
