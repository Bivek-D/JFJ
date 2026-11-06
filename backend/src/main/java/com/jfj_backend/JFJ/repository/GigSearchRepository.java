package com.jfj_backend.JFJ.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.jfj_backend.JFJ.dto.request.GigFilters;
import com.jfj_backend.JFJ.dto.response.CategoryResponse;
import com.jfj_backend.JFJ.dto.response.GigSummaryResponse;
import com.jfj_backend.JFJ.dto.response.MapPinResponse;
import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

/**
 * Worker-side gig search in plain SQL. Why not JPA here: the filters are optional (the WHERE clause is
 * built piece by piece) and the PostGIS functions are SQL anyway. Every value goes in as a :named
 * parameter, never glued into the SQL text, so input can't change the query (no SQL injection).
 *
 * Search, radius, map and distance use public_location only (PRD 6.3). The exact point is never read here.
 * Coordinates are never logged (MAP-5).
 */
@Repository
public class GigSearchRepository {

	/** A finished query: SQL text + its parameters. Package-visible builders let tests EXPLAIN the real SQL. */
	public record Query(String sql, MapSqlParameterSource params) {
	}

	private static final String FROM = """
			FROM gigs g
			JOIN users u ON u.id = g.provider_id
			JOIN categories c ON c.id = g.category_id
			""";

	// The worker's position as a geography point. ST_MakePoint takes (x, y) = (lng, lat).
	private static final String HERE = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

	private final NamedParameterJdbcTemplate jdbc;

	public GigSearchRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ---------- list (GET /gigs) ----------

	/** lat/lng/radiusM all null = no location: newest first, distance null. */
	public List<GigSummaryResponse> searchList(GigFilters filters, Double lat, Double lng, Double radiusM,
			LocalDate today, int limit, long offset) {
		Query query = listQuery(filters, lat, lng, radiusM, today, limit, offset);
		return jdbc.query(query.sql(), query.params(), (rs, i) -> toSummary(rs));
	}

	public long countList(GigFilters filters, Double lat, Double lng, Double radiusM, LocalDate today) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = where(filters, lat, lng, radiusM, today, params);
		Long count = jdbc.queryForObject("SELECT count(*) " + FROM + where, params, Long.class);
		return count == null ? 0 : count;
	}

	public static Query listQuery(GigFilters filters, Double lat, Double lng, Double radiusM, LocalDate today,
			int limit, long offset) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = where(filters, lat, lng, radiusM, today, params);
		boolean located = lat != null;
		String distance = located ? "ST_Distance(g.public_location::geography, " + HERE + ")" : "NULL::float8";
		// g.id last: a stable order, so page 2 never repeats or skips a gig that tied on the other keys.
		String orderBy = located ? "distance_m, g.created_at DESC, g.id" : "g.created_at DESC, g.id";
		String sql = """
				SELECT g.id, g.title, c.id AS category_id, c.name AS category_name, g.pay_type, g.pay_amount,
				       g.workers_needed, g.min_experience, g.start_date, g.duration_text, g.locality, g.city,
				       ST_Y(g.public_location) AS lat, ST_X(g.public_location) AS lng,
				       %s AS distance_m, g.created_at
				%s%s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(distance, FROM, where, orderBy);
		params.addValue("limit", limit).addValue("offset", offset);
		return new Query(sql, params);
	}

	// ---------- map (GET /gigs/map) ----------

	public List<MapPinResponse> searchMap(GigFilters filters, double minLat, double minLng, double maxLat,
			double maxLng, LocalDate today, int limit) {
		Query query = mapQuery(filters, minLat, minLng, maxLat, maxLng, today, limit);
		return jdbc.query(query.sql(), query.params(), (rs, i) -> new MapPinResponse(
				rs.getObject("id", UUID.class), rs.getString("title"), rs.getBigDecimal("pay_amount"),
				PayType.valueOf(rs.getString("pay_type")), rs.getLong("category_id"),
				rs.getDouble("lat"), rs.getDouble("lng")));
	}

	public static Query mapQuery(GigFilters filters, double minLat, double minLng, double maxLat, double maxLng,
			LocalDate today, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		String where = where(filters, null, null, null, today, params)
				// && = "bounding boxes overlap": the cheap test the GiST index on public_location answers.
				+ " AND g.public_location && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)\n";
		params.addValue("minLat", minLat).addValue("minLng", minLng)
				.addValue("maxLat", maxLat).addValue("maxLng", maxLng).addValue("limit", limit);
		String sql = """
				SELECT g.id, g.title, g.pay_amount, g.pay_type, g.category_id,
				       ST_Y(g.public_location) AS lat, ST_X(g.public_location) AS lng
				%s%s
				ORDER BY g.created_at DESC, g.id
				LIMIT :limit
				""".formatted(FROM, where);
		return new Query(sql, params);
	}

	// ---------- shared WHERE ----------

	/**
	 * Builds the WHERE clause and fills params. Only conditions for filters that were sent are added.
	 * Always: OPEN gigs of ACTIVE providers that haven't started and are still taking applications
	 * (this stands in for the expiry job until it exists).
	 */
	private static String where(GigFilters f, Double lat, Double lng, Double radiusM, LocalDate today,
			MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder("""
				WHERE g.status = 'OPEN'
				  AND u.status = 'ACTIVE'
				  AND g.start_date >= :today
				  AND (g.apply_deadline IS NULL OR g.apply_deadline >= :today)
				""");
		params.addValue("today", today);

		if (lat != null) {
			// ST_DWithin on geography = "within N metres", and it can use the geography GiST index.
			where.append("  AND ST_DWithin(g.public_location::geography, ").append(HERE).append(", :radiusM)\n");
			params.addValue("lat", lat).addValue("lng", lng).addValue("radiusM", radiusM);
		}
		if (f.categoryId() != null) {
			where.append("  AND g.category_id = :categoryId\n");
			params.addValue("categoryId", f.categoryId());
		}
		if (f.minPay() != null) {
			where.append("  AND g.pay_amount >= :minPay\n");
			params.addValue("minPay", f.minPay());
		}
		if (f.maxPay() != null) {
			where.append("  AND g.pay_amount <= :maxPay\n");
			params.addValue("maxPay", f.maxPay());
		}
		if (f.payType() != null) {
			where.append("  AND g.pay_type = :payType\n");
			params.addValue("payType", f.payType().name());
		}
		if (f.experience() != null) {
			// A worker at INTERMEDIATE sees gigs needing nothing, BEGINNER or INTERMEDIATE.
			List<String> allowed = Arrays.stream(ExperienceLevel.values())
					.filter(level -> level.ordinal() <= f.experience().ordinal())
					.map(Enum::name).toList();
			where.append("  AND (g.min_experience IS NULL OR g.min_experience IN (:levels))\n");
			params.addValue("levels", allowed);
		}
		if (f.startFrom() != null) {
			where.append("  AND g.start_date >= :startFrom\n");
			params.addValue("startFrom", f.startFrom());
		}
		if (f.q() != null && !f.q().isBlank()) {
			// websearch_to_tsquery understands what people type: words, "quoted phrases", -excluded.
			where.append("  AND g.search_vector @@ websearch_to_tsquery('english', :q)\n");
			params.addValue("q", f.q().trim());
		}
		return where.toString();
	}

	private static GigSummaryResponse toSummary(ResultSet rs) throws SQLException {
		BigDecimal distanceM = rs.getBigDecimal("distance_m");
		String minExperience = rs.getString("min_experience");
		return new GigSummaryResponse(rs.getObject("id", UUID.class), rs.getString("title"),
				new CategoryResponse(rs.getLong("category_id"), rs.getString("category_name")),
				PayType.valueOf(rs.getString("pay_type")), rs.getBigDecimal("pay_amount"),
				rs.getInt("workers_needed"), minExperience == null ? null : ExperienceLevel.valueOf(minExperience),
				rs.getObject("start_date", LocalDate.class), rs.getString("duration_text"), rs.getString("locality"),
				rs.getString("city"), rs.getDouble("lat"), rs.getDouble("lng"),
				distanceM == null ? null : roundToHalfKm(distanceM.doubleValue()),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	/** FIND-5: 1,240 m -> 1.0 km, 1,260 m -> 1.5 km. Rounding hides small differences in position. */
	public static double roundToHalfKm(double metres) {
		return Math.round(metres / 500.0) / 2.0;
	}
}
