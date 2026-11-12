package com.jfj_backend.JFJ.util;

import java.util.random.RandomGenerator;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Watch the order: JTS and PostGIS use x = longitude, y = latitude. Our API and Leaflet say lat first.
 * Every conversion goes through point() / lat() / lng() here, so the swap happens in one place only.
 */
public final class GeoUtils {

	public static final int SRID = 4326;

	/** Mean Earth radius in metres. Treating the Earth as a sphere is off by < 0.5%, fine for 300-500 m. */
	static final double EARTH_RADIUS_M = 6_371_008.8;

	public static final double MIN_OFFSET_M = 300;
	public static final double MAX_OFFSET_M = 500;

	private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

	private GeoUtils() {
	}

	public static Point point(double lat, double lng) {
		return FACTORY.createPoint(new Coordinate(lng, lat));
	}

	public static double lat(Point point) {
		return point.getY();
	}

	public static double lng(Point point) {
		return point.getX();
	}

	/**
	 * The public point: the exact point moved 300-500 m in a random direction.
	 * Called once per exact point and stored; never re-randomised per request, or averaging many
	 * answers would lead back to the exact spot.
	 */
	public static Point approximate(Point exact, RandomGenerator random) {
		double distance = random.nextDouble(MIN_OFFSET_M, MAX_OFFSET_M);
		double bearing = random.nextDouble(0, 360);
		return destination(exact, distance, bearing);
	}

	public static Point destination(Point start, double distanceM, double bearingDeg) {
		double lat1 = Math.toRadians(lat(start));
		double lng1 = Math.toRadians(lng(start));
		double bearing = Math.toRadians(bearingDeg);
		double angular = distanceM / EARTH_RADIUS_M;

		double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular)
				+ Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing));
		double lng2 = lng1 + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
				Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
		return point(Math.toDegrees(lat2), Math.toDegrees(lng2));
	}

	public static double distanceM(Point a, Point b) {
		double dLat = Math.toRadians(lat(b) - lat(a));
		double dLng = Math.toRadians(lng(b) - lng(a));
		double h = Math.pow(Math.sin(dLat / 2), 2)
				+ Math.cos(Math.toRadians(lat(a))) * Math.cos(Math.toRadians(lat(b))) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
	}
}
