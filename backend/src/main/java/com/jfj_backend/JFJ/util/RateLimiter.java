package com.jfj_backend.JFJ.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed window, in memory: fine for one server instance. Protects the free Geoapify quota from one
 * user (or a script) burning it all.
 */
public class RateLimiter {

	private record Window(Instant start, int count) {
	}

	private final int limit;
	private final Duration window;
	private final Clock clock;
	private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(int limit, Duration window, Clock clock) {
		this.limit = limit;
		this.window = window;
		this.clock = clock;
	}

	public boolean tryAcquire(UUID userId) {
		Instant now = clock.instant();
		// compute() runs atomically per key, so two parallel requests can't both take the last slot.
		Window updated = windows.compute(userId, (id, current) -> {
			if (current == null || !now.isBefore(current.start().plus(window))) {
				return new Window(now, 1);
			}
			return new Window(current.start(), current.count() + 1);
		});
		return updated.count() <= limit;
	}
}
