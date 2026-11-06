package com.jfj_backend.JFJ.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.request.GigFilters;
import com.jfj_backend.JFJ.dto.request.GigMapParams;
import com.jfj_backend.JFJ.dto.request.GigSearchParams;
import com.jfj_backend.JFJ.dto.response.GigSummaryResponse;
import com.jfj_backend.JFJ.dto.response.MapPinResponse;
import com.jfj_backend.JFJ.dto.response.MapResponse;
import com.jfj_backend.JFJ.dto.response.PageResponse;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.repository.GigSearchRepository;
import com.jfj_backend.JFJ.util.AgeUtils;

/** Worker discovery (PRD 5.4, 6.2): the gig list and the map. Checks the combinations, then asks the repository. */
@Service
public class GigSearchService {

	public static final int DEFAULT_RADIUS_KM = 10;
	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAP_LIMIT = 200;

	private final GigSearchRepository repository;

	public GigSearchService(GigSearchRepository repository) {
		this.repository = repository;
	}

	/**
	 * Sorted by distance when lat/lng are sent, else newest first (FIND-1).
	 * Not yet: falling back to the user's saved location (users.location is not filled by any endpoint yet).
	 */
	@Transactional(readOnly = true)
	public PageResponse<GigSummaryResponse> list(GigSearchParams params) {
		if ((params.lat() == null) != (params.lng() == null)) {
			throw new BadRequestException("Send both lat and lng, or neither");
		}
		if (params.radiusKm() != null && params.lat() == null) {
			throw new BadRequestException("radiusKm needs lat and lng");
		}
		GigFilters filters = checkFilters(params.filters());
		Double radiusM = params.lat() == null ? null
				: 1000.0 * (params.radiusKm() == null ? DEFAULT_RADIUS_KM : params.radiusKm());
		int page = params.page() == null ? 0 : params.page();
		int size = params.size() == null ? DEFAULT_PAGE_SIZE : params.size();
		LocalDate today = AgeUtils.todayInIndia();

		List<GigSummaryResponse> items = repository.searchList(filters, params.lat(), params.lng(), radiusM, today,
				size, (long) page * size);
		long total = repository.countList(filters, params.lat(), params.lng(), radiusM, today);
		return new PageResponse<>(items, page, size, total, (int) Math.ceil(total / (double) size));
	}

	/** MAP-2: at most 200 pins. Ask for 201: if 201 come back, there are more, so truncated = true. */
	@Transactional(readOnly = true)
	public MapResponse map(GigMapParams params) {
		if (params.minLat() >= params.maxLat() || params.minLng() >= params.maxLng()) {
			throw new BadRequestException("The map area is empty: min must be below max");
		}
		List<MapPinResponse> pins = repository.searchMap(checkFilters(params.filters()), params.minLat(),
				params.minLng(), params.maxLat(), params.maxLng(), AgeUtils.todayInIndia(), MAP_LIMIT + 1);
		boolean truncated = pins.size() > MAP_LIMIT;
		return new MapResponse(truncated ? pins.subList(0, MAP_LIMIT) : pins, truncated);
	}

	private static GigFilters checkFilters(GigFilters filters) {
		if (filters.minPay() != null && filters.maxPay() != null && filters.minPay().compareTo(filters.maxPay()) > 0) {
			throw new BadRequestException("minPay can't be more than maxPay");
		}
		return filters;
	}
}
