package com.jfj_backend.JFJ.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.jfj_backend.JFJ.config.GeoapifyProperties;
import com.jfj_backend.JFJ.dto.response.AddressResponse;
import com.jfj_backend.JFJ.exception.ResourceNotFoundException;
import com.jfj_backend.JFJ.exception.ServiceUnavailableException;
import com.jfj_backend.JFJ.exception.TooManyRequestsException;
import com.jfj_backend.JFJ.util.RateLimiter;
import com.jfj_backend.JFJ.util.TtlCache;

import tools.jackson.databind.JsonNode;

/**
 * The browser calls us and we call Geoapify, so the API key never reaches the browser.
 * Never logged: the search text, coordinates, or Geoapify's error message (it contains the URL, key included).
 */
@Service
@EnableConfigurationProperties(GeoapifyProperties.class)
public class GeoService {

	private static final Logger log = LoggerFactory.getLogger(GeoService.class);
	private static final String UNAVAILABLE = "Address search is not available right now; drop the pin and type the address";
	private static final int SUGGESTIONS = 5;

	private final GeoapifyProperties properties;
	private final RestClient client;
	private final RateLimiter rateLimiter;
	private final TtlCache<String, List<AddressResponse>> cache;

	public GeoService(GeoapifyProperties properties) {
		this.properties = properties;
		// Timeouts: without them a slow Geoapify would hold our request threads open indefinitely.
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		factory.setReadTimeout(properties.readTimeout());
		// Spring Boot 4 only provides a RestClient.Builder bean with the optional restclient starter; the
		// plain builder is enough here and already reads JSON with Jackson 3 (on the classpath).
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
		Clock clock = Clock.systemUTC();
		this.rateLimiter = new RateLimiter(properties.requestsPerMinute(), Duration.ofMinutes(1), clock);
		this.cache = new TtlCache<>(2000, properties.cacheTtl(), clock);
	}

	public List<AddressResponse> autocomplete(UUID userId, String query) {
		checkAllowed(userId);
		String text = query.trim();
		String cacheKey = "a:" + text.toLowerCase(Locale.ROOT);
		return cache.get(cacheKey).orElseGet(() -> {
			List<AddressResponse> found = fetch(uri -> uri.path("/v1/geocode/autocomplete")
					.queryParam("text", text)
					.queryParam("filter", "countrycode:in")
					.queryParam("lang", "en")
					.queryParam("limit", SUGGESTIONS)
					.queryParam("apiKey", properties.apiKey())
					.build());
			cache.put(cacheKey, found);
			return found;
		});
	}

	/**
	 * The point is rounded to 4 decimals (~11 m): close enough for a street address, and nearby drops
	 * share one cache entry.
	 */
	public AddressResponse reverse(UUID userId, double lat, double lng) {
		checkAllowed(userId);
		double roundedLat = Math.round(lat * 10_000) / 10_000.0;
		double roundedLng = Math.round(lng * 10_000) / 10_000.0;
		String cacheKey = "r:" + roundedLat + "," + roundedLng;
		List<AddressResponse> found = cache.get(cacheKey).orElseGet(() -> {
			List<AddressResponse> result = fetch(uri -> uri.path("/v1/geocode/reverse")
					.queryParam("lat", roundedLat)
					.queryParam("lon", roundedLng)
					.queryParam("lang", "en")
					.queryParam("apiKey", properties.apiKey())
					.build());
			cache.put(cacheKey, result);
			return result;
		});
		if (found.isEmpty()) {
			throw new ResourceNotFoundException("No address found here; type it in");
		}
		return found.getFirst();
	}

	private void checkAllowed(UUID userId) {
		if (!properties.configured()) {
			throw new ServiceUnavailableException(UNAVAILABLE);
		}
		if (!rateLimiter.tryAcquire(userId)) {
			throw new TooManyRequestsException("Too many address searches; wait a moment and try again");
		}
	}

	private List<AddressResponse> fetch(Function<UriBuilder, URI> uri) {
		try {
			JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
			return parse(body);
		}
		catch (RestClientException ex) {
			// Bad key, Geoapify down, quota used up, or timeout. Only the exception type is logged.
			log.warn("Geoapify request failed: {}", ex.getClass().getSimpleName());
			throw new ServiceUnavailableException(UNAVAILABLE);
		}
	}

	static List<AddressResponse> parse(JsonNode body) {
		List<AddressResponse> addresses = new ArrayList<>();
		if (body == null) {
			return addresses;
		}
		for (JsonNode feature : body.path("features")) {
			JsonNode p = feature.path("properties");
			if (!p.path("lat").isNumber() || !p.path("lon").isNumber()) {
				continue;
			}
			addresses.add(new AddressResponse(text(p, "formatted"), text(p, "address_line1"),
					firstText(p, "suburb", "district", "neighbourhood"), text(p, "city"), text(p, "state"),
					text(p, "postcode"), p.path("lat").asDouble(), p.path("lon").asDouble()));
		}
		return addresses;
	}

	// Fields can be missing depending on the place (Geoapify docs), so every one is optional.
	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return (value.isMissingNode() || value.isNull() || value.asString().isBlank()) ? null : value.asString();
	}

	private static String firstText(JsonNode node, String... fields) {
		for (String field : fields) {
			String value = text(node, field);
			if (value != null) {
				return value;
			}
		}
		return null;
	}
}
