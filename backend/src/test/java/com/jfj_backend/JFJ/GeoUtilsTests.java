package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import com.jfj_backend.JFJ.util.GeoUtils;

// Plain unit tests: no Spring, no database.
class GeoUtilsTests {

	private final Point guwahati = GeoUtils.point(26.1445, 91.7362);

	@Test
	void pointStoresLngAsXAndLatAsY() {
		assertThat(guwahati.getX()).isEqualTo(91.7362);
		assertThat(guwahati.getY()).isEqualTo(26.1445);
		assertThat(guwahati.getSRID()).isEqualTo(4326);
	}

	@Test
	void destinationGoesTheRightWay() {
		// 1 km north: latitude grows by about 0.009 degrees, longitude stays.
		Point north = GeoUtils.destination(guwahati, 1000, 0);
		assertThat(GeoUtils.lat(north)).isCloseTo(26.1445 + 0.008993, within(0.00001));
		assertThat(GeoUtils.lng(north)).isCloseTo(91.7362, within(0.000001));
		assertThat(GeoUtils.distanceM(guwahati, north)).isCloseTo(1000, within(0.5));

		// East: longitude grows, latitude roughly stays.
		Point east = GeoUtils.destination(guwahati, 1000, 90);
		assertThat(GeoUtils.lng(east)).isGreaterThan(91.7362);
		assertThat(GeoUtils.distanceM(guwahati, east)).isCloseTo(1000, within(0.5));
	}

	@Test
	void approximatePointIsAlways300To500mAway() {
		SplittableRandom random = new SplittableRandom(42);
		for (int i = 0; i < 10_000; i++) {
			double d = GeoUtils.distanceM(guwahati, GeoUtils.approximate(guwahati, random));
			assertThat(d).isBetween(GeoUtils.MIN_OFFSET_M - 0.01, GeoUtils.MAX_OFFSET_M + 0.01);
		}
	}
}
