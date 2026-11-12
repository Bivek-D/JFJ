package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.request.GigFilters;
import com.jfj_backend.JFJ.entity.Category;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.Mode;
import com.jfj_backend.JFJ.enums.PayType;
import com.jfj_backend.JFJ.enums.UserStatus;
import com.jfj_backend.JFJ.repository.CategoryRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.GigSearchRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.security.TokenService;
import com.jfj_backend.JFJ.util.AgeUtils;
import com.jfj_backend.JFJ.util.GeoUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GigSearchTests {

	// Worker stands in central Guwahati. Shillong is ~65 km away.
	private static final double LAT = 26.1445, LNG = 91.7362;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository users;

	@Autowired
	CategoryRepository categories;

	@Autowired
	GigRepository gigs;

	@Autowired
	TokenService tokenService;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	NamedParameterJdbcTemplate namedJdbc;

	// Fixed seed: the approximate points come out the same on every run.
	private final SplittableRandom random = new SplittableRandom(7);

	private User provider;
	private User worker;
	private LocalDate today;

	@BeforeEach
	void setUp() {
		provider = users.saveAndFlush(new User("Meena", "meena@example.com", "9678901234", "$2a$10$hash",
				LocalDate.of(1985, 1, 10), "8 Station Road", "Guwahati", "Assam", "781001"));
		worker = users.saveAndFlush(new User("Ritu", "ritu@example.com", "9567890123", "$2a$10$hash",
				LocalDate.of(1997, 8, 15), "4 Tea Garden Lane", "Guwahati", "Assam", "781005"));
		today = AgeUtils.todayInIndia();
	}

	private Category category(String name) {
		return categories.findAll().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
	}

	private Gig gig(String title, double lat, double lng, String category, PayType payType, String pay,
			ExperienceLevel minExperience) {
		Gig gig = new Gig(provider, GeoUtils.point(lat, lng), random);
		gig.updateDetails(category(category), title, title + " for a family event", payType, new BigDecimal(pay), 2,
				minExperience, today.plusDays(5), "1 day", null, "1 Road", "Locality", "City", java.util.Set.of());
		gig.publish();
		return gigs.saveAndFlush(gig);
	}

	private Gig gig(String title, double lat, double lng) {
		return gig(title, lat, lng, "Events", PayType.DAILY, "500.00", null);
	}

	private ResultActions search(String query) throws Exception {
		return mockMvc.perform(get("/api/v1/gigs" + query)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.createAccessToken(worker, Mode.WORKER,
						Instant.now())));
	}

	private void bulkInsert(int n, double minLat, double minLng, double maxLat, double maxLng, String title) {
		jdbc.update("""
				INSERT INTO gigs (provider_id, category_id, title, description, pay_type, pay_amount, workers_needed,
				                  start_date, duration_text, address_line, locality, city, location, public_location, status)
				SELECT ?, ?, ? || ' ' || i, 'Bulk test gig', 'DAILY', 500, 1, ?, '1 day', 'x', 'y', 'z', pt, pt, 'OPEN'
				FROM (SELECT i, ST_SetSRID(ST_MakePoint(? + random() * (? - ?), ? + random() * (? - ?)), 4326) AS pt
				      FROM generate_series(1, ?) AS i) s
				""", provider.getId(), category("Other").getId(), title, today.plusDays(5),
				minLng, maxLng, minLng, minLat, maxLat, minLat, n);
	}

	@Test
	void radiusFindsNearGigsSortedByDistance() throws Exception {
		gig("Far wedding", 25.5788, 91.8933);         // Shillong, ~65 km
		gig("Near wedding", LAT + 0.02, LNG);          // ~2.2 km north
		gig("Next door wedding", LAT, LNG);            // only the 300-500 m offset away

		search("?lat=" + LAT + "&lng=" + LNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].title", contains("Next door wedding", "Near wedding")))
				.andExpect(jsonPath("$.totalItems").value(2))
				// 300-500 m rounds to 0.5 km.
				.andExpect(jsonPath("$.items[0].distanceKm").value(0.5));

		search("?lat=" + LAT + "&lng=" + LNG + "&radiusKm=50")
				.andExpect(jsonPath("$.items", hasSize(2)));
		search("?lat=" + LAT + "&lng=" + LNG + "&radiusKm=50&size=1")
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void withoutLocationNewestFirstAndNoDistance() throws Exception {
		gig("Older", LAT, LNG);
		gig("Newer", 25.5788, 91.8933);

		search("").andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].title", contains("Newer", "Older")))
				.andExpect(jsonPath("$.items[0].distanceKm").doesNotExist());
	}

	@Test
	void onlyLiveGigsOfActiveProvidersAreShown() throws Exception {
		gig("Visible", LAT, LNG);
		Gig draft = new Gig(provider, GeoUtils.point(LAT, LNG), random);
		draft.updateDetails(category("Events"), "Draft gig", "Not published", PayType.DAILY, new BigDecimal("500"),
				1, null, today.plusDays(5), "1 day", null, "1 Road", "L", "C", java.util.Set.of());
		gigs.saveAndFlush(draft);
		Gig started = gig("Already started", LAT, LNG);
		Gig closed = gig("Deadline passed", LAT, LNG);
		jdbc.update("UPDATE gigs SET start_date = ? WHERE id = ?", today.minusDays(1), started.getId());
		jdbc.update("UPDATE gigs SET apply_deadline = ? WHERE id = ?", today.minusDays(1), closed.getId());

		search("").andExpect(jsonPath("$.items[*].title", contains("Visible")));

		provider.setStatus(UserStatus.SUSPENDED);
		users.saveAndFlush(provider);
		search("").andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void filtersNarrowTheList() throws Exception {
		gig("Cook for a party", LAT, LNG, "Cooking & Catering", PayType.HOURLY, "150.50", null);
		gig("Wedding helpers needed", LAT, LNG, "Events", PayType.DAILY, "800.00", ExperienceLevel.INTERMEDIATE);
		gig("Expert electrician", LAT, LNG, "Construction & Repair", PayType.FIXED, "2500.00",
				ExperienceLevel.EXPERIENCED);

		search("?categoryId=" + category("Events").getId())
				.andExpect(jsonPath("$.items[*].title", contains("Wedding helpers needed")));
		search("?minPay=200&maxPay=1000")
				.andExpect(jsonPath("$.items[*].title", contains("Wedding helpers needed")));
		search("?payType=HOURLY")
				.andExpect(jsonPath("$.items[*].title", contains("Cook for a party")));
		search("?experience=INTERMEDIATE")
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[*].title", org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.hasItem("Expert electrician"))));
		search("?q=weddings").andExpect(jsonPath("$.items[*].title", contains("Wedding helpers needed")));
		search("?startFrom=" + today.plusDays(30)).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void badSearchParamsAre400() throws Exception {
		search("?lat=" + LAT).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Send both lat and lng, or neither"));
		search("?lat=" + LAT + "&lng=" + LNG + "&radiusKm=500").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.radiusKm").exists());
		search("?lat=abc&lng=" + LNG).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.lat").exists());
		search("?minPay=900&maxPay=100").andExpect(status().isBadRequest());
		search("/map?minLat=26.2&minLng=91.7&maxLat=26.1&maxLng=91.8").andExpect(status().isBadRequest());
		search("/map").andExpect(status().isBadRequest());
	}

	@Test
	void mapReturnsPinsInsideTheVisibleArea() throws Exception {
		Gig near = gig("In view", LAT, LNG);
		gig("Out of view", 25.5788, 91.8933);

		search("/map?minLat=26.0&minLng=91.6&maxLat=26.3&maxLng=91.9")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.truncated").value(false))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].id").value(near.getId().toString()))
				// The pin is the approximate point, never the exact one.
				.andExpect(jsonPath("$.items[0].lat").value(GeoUtils.lat(near.getPublicLocation())))
				.andExpect(jsonPath("$.items[0].lng").value(GeoUtils.lng(near.getPublicLocation())));
	}

	@Test
	void mapStopsAt200AndSaysTruncated() throws Exception {
		bulkInsert(205, 26.10, 91.70, 26.20, 91.80, "Crowded");
		search("/map?minLat=26.0&minLng=91.6&maxLat=26.3&maxLng=91.9")
				.andExpect(jsonPath("$.items", hasSize(200)))
				.andExpect(jsonPath("$.truncated").value(true));
	}

	/**
	 * With many gigs spread over India, the planner should pick the spatial indexes for small areas
	 * instead of reading the whole table. EXPLAIN shows the plan without running the query.
	 */
	@Test
	void searchesUseTheIndexes() {
		bulkInsert(5000, 8.0, 68.0, 36.0, 97.0, "Spread");
		gig("Saxophone player for a wedding", LAT, LNG);
		jdbc.execute("ANALYZE gigs");
		GigFilters none = new GigFilters(null, null, null, null, null, null, null);

		String radius = explain(GigSearchRepository.listQuery(none, LAT, LNG, 5000.0, today, 20, 0));
		assertThat(radius).contains("idx_gigs_public_location_geog");

		String map = explain(GigSearchRepository.mapQuery(none, 26.1, 91.7, 26.2, 91.8, today, 201));
		assertThat(map).contains("idx_gigs_public_location");

		// On 5,000 short rows Postgres rightly finds a full read cheaper than the word index, so the text
		// check forces its hand: with full reads (seq scan) and full index walks (index scan) switched off
		// for this transaction, a bitmap scan on an index whose condition matches is all that's left.
		// That proves the real SQL CAN use the GIN index, which Postgres picks by itself at real sizes.
		jdbc.execute("SET LOCAL enable_seqscan = off");
		jdbc.execute("SET LOCAL enable_indexscan = off");
		GigFilters keyword = new GigFilters(null, null, null, null, null, null, "saxophone");
		String text = explain(GigSearchRepository.listQuery(keyword, null, null, null, today, 20, 0));
		assertThat(text).contains("idx_gigs_search_vector");
	}

	private String explain(GigSearchRepository.Query query) {
		List<String> lines = namedJdbc.queryForList("EXPLAIN " + query.sql(), query.params(), String.class);
		return String.join("\n", lines);
	}
}
