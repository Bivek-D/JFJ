package com.jfj_backend.JFJ.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A small in-memory cache: entries expire after ttl, and when full the least recently used one is
 * dropped. Good enough for one server instance (Render free runs one). Saves Geoapify credits when
 * people type the same thing, and makes repeated lookups instant.
 * synchronized = one thread at a time, because LinkedHashMap is not safe for parallel use.
 */
public class TtlCache<K, V> {

	private record Entry<V>(V value, Instant expiresAt) {
	}

	private final Duration ttl;
	private final Clock clock;
	private final Map<K, Entry<V>> map;

	public TtlCache(int maxEntries, Duration ttl, Clock clock) {
		this.ttl = ttl;
		this.clock = clock;
		// accessOrder = true + removeEldestEntry: the standard Java recipe for an LRU cache.
		this.map = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
				return size() > maxEntries;
			}
		};
	}

	public synchronized Optional<V> get(K key) {
		Entry<V> entry = map.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (clock.instant().isAfter(entry.expiresAt())) {
			map.remove(key);
			return Optional.empty();
		}
		return Optional.of(entry.value());
	}

	public synchronized void put(K key, V value) {
		map.put(key, new Entry<>(value, clock.instant().plus(ttl)));
	}
}
