package com.jfj_backend.JFJ.dto.response;

/**
 * Our own field names, so switching geocoding provider doesn't change the API.
 * Any field except lat/lng can be null: coverage is patchy in small towns.
 */
public record AddressResponse(String label, String addressLine, String locality, String city, String state,
		String pincode, double lat, double lng) {
}
