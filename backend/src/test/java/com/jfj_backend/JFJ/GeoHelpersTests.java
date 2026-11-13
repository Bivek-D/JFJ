package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jfj_backend.JFJ.util.RateLimiter;
import com.jfj_backend.JFJ.util.TtlCache;

// Unit tests with a hand-moved clock: time is controlled, so no sleeping.
class GeoHelpersTests {

	static class MovableClock extends Clock {
		Instant now = Instant.parse("2026-11-07T10:00:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}

	@Test
	void cacheEntriesExpireAfterTtl() {
		MovableClock clock = new MovableClock();
		TtlCache<String, String> cache = new TtlCache<>(10, Duration.ofMinutes(10), clock);
		cache.put("k", "v");
		clock.now = clock.now.plus(Duration.ofMinutes(9));
		assertThat(cache.get("k")).contains("v");
		clock.now = clock.now.plus(Duration.ofMinutes(2));
		assertThat(cache.get("k")).isEmpty();
	}

	@Test
	void cacheDropsLeastRecentlyUsedWhenFull() {
		TtlCache<String, String> cache = new TtlCache<>(2, Duration.ofMinutes(10), new MovableClock());
		cache.put("a", "1");
		cache.put("b", "2");
		cache.get("a");          // a is now more recent than b
		cache.put("c", "3");     // over the limit: b goes
		assertThat(cache.get("a")).contains("1");
		assertThat(cache.get("b")).isEmpty();
		assertThat(cache.get("c")).contains("3");
	}

	@Test
	void rateLimitResetsEachWindowAndIsPerUser() {
		MovableClock clock = new MovableClock();
		RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
		UUID asha = UUID.randomUUID();
		UUID ravi = UUID.randomUUID();

		assertThat(limiter.tryAcquire(asha)).isTrue();
		assertThat(limiter.tryAcquire(asha)).isTrue();
		assertThat(limiter.tryAcquire(asha)).isFalse();
		assertThat(limiter.tryAcquire(ravi)).isTrue();   // someone else isn't affected

		clock.now = clock.now.plus(Duration.ofMinutes(1));
		assertThat(limiter.tryAcquire(asha)).isTrue();   // new window
	}
}
