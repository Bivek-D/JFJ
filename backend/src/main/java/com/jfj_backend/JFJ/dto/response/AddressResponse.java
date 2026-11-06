package com.jfj_backend.JFJ.dto.response;

/**
 * One address from Geoapify, in our own field names (so switching provider later doesn't change the API).
 * Any field except lat/lng can be null: coverage is patchy in small towns (LOC-5).
 * label = the full one-line address for the suggestion list.
 */
public record AddressResponse(String label, String addressLine, String locality, String city, String state,
		String pincode, double lat, double lng) {
}
