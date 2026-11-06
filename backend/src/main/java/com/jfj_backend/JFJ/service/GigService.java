package com.jfj_backend.JFJ.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.request.GigRequest;
import com.jfj_backend.JFJ.dto.response.OwnerGigResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.exception.ForbiddenException;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.util.AgeUtils;
import com.jfj_backend.JFJ.util.GeoUtils;

/**
 * Provider side of gigs (PRD 5.3): create as DRAFT, edit, publish, delete a draft, list my gigs.
 * The rules about state and location live in Gig itself; this class loads, checks ownership and dates,
 * and builds responses inside the transaction.
 */
@Service
public class GigService {

	public static final int MAX_PAGE_SIZE = 50;

	private final GigRepository gigs;
	private final CatalogService catalogService;
	private final UserService userService;
	// SecureRandom: the offset direction must not be guessable, or the exact point could be worked back.
	private final RandomGenerator random = new SecureRandom();

	public GigService(GigRepository gigs, CatalogService catalogService, UserService userService) {
		this.gigs = gigs;
		this.catalogService = catalogService;
		this.userService = userService;
	}

	@Transactional
	public OwnerGigResponse create(UUID providerId, GigRequest request) {
		checkDates(request);
		Gig gig = new Gig(userService.getById(providerId), GeoUtils.point(request.lat(), request.lng()), random);
		applyDetails(gig, request);
		return OwnerGigResponse.from(gigs.saveAndFlush(gig));
	}

	/** PUT = full replace. Moving the pin counts as a location edit (max 3). */
	@Transactional
	public OwnerGigResponse update(UUID providerId, UUID gigId, GigRequest request) {
		Gig gig = getOwn(providerId, gigId);
		checkDates(request);
		applyDetails(gig, request);
		gig.moveTo(GeoUtils.point(request.lat(), request.lng()), random);
		return OwnerGigResponse.from(gigs.saveAndFlush(gig));
	}

	@Transactional
	public OwnerGigResponse publish(UUID providerId, UUID gigId) {
		Gig gig = getOwn(providerId, gigId);
		// The draft may have been saved days ago: the start date must still be ahead.
		if (gig.getStartDate().isBefore(AgeUtils.todayInIndia())) {
			throw new BadRequestException("The start date has passed; change it before publishing");
		}
		gig.publish();
		return OwnerGigResponse.from(gig);
	}

	/** Only drafts can be deleted (PRD 4.4). A published gig is cancelled instead (B4). */
	@Transactional
	public void delete(UUID providerId, UUID gigId) {
		Gig gig = getOwn(providerId, gigId);
		if (!gig.isDraft()) {
			throw new BadRequestException("Only a draft can be deleted");
		}
		gigs.delete(gig);
	}

	/** GET /gigs/mine, newest first, optionally one status only. */
	@Transactional(readOnly = true)
	public PageResponse<OwnerGigResponse> listMine(UUID providerId, GigStatus status, int page, int size) {
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Gig> result = (status == null)
				? gigs.findByProviderId(providerId, pageRequest)
				: gigs.findByProviderIdAndStatus(providerId, status, pageRequest);
		return PageResponse.from(result, OwnerGigResponse::from);
	}

	/** 404 if the gig doesn't exist, 403 if it belongs to someone else. */
	private Gig getOwn(UUID providerId, UUID gigId) {
		Gig gig = gigs.findById(gigId).orElseThrow(() -> new ResourceNotFoundException("Gig not found"));
		if (!gig.isOwnedBy(providerId)) {
			throw new ForbiddenException("You can only change your own gigs");
		}
		return gig;
	}

	private void applyDetails(Gig gig, GigRequest request) {
		gig.updateDetails(catalogService.requireActiveCategory(request.categoryId()), request.title().trim(),
				request.description().trim(), request.payType(), request.payAmount(), request.workersNeeded(),
				request.minExperience(), request.startDate(), request.durationText().trim(),
				request.applyDeadline(), request.addressLine().trim(), request.locality().trim(),
				request.city().trim(), catalogService.requireSkills(request.skillIds()));
	}

	/** "Today" is today in India, whatever the server's clock zone. */
	private static void checkDates(GigRequest request) {
		LocalDate today = AgeUtils.todayInIndia();
		if (request.startDate().isBefore(today)) {
			throw new BadRequestException("The start date can't be in the past");
		}
		if (request.applyDeadline() != null) {
			if (request.applyDeadline().isBefore(today)) {
				throw new BadRequestException("The apply deadline can't be in the past");
			}
			if (request.applyDeadline().isAfter(request.startDate())) {
				throw new BadRequestException("The apply deadline must be on or before the start date");
			}
		}
	}
}
