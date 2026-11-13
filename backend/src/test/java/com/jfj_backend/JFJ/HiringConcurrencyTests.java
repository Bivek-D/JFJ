package com.jfj_backend.JFJ;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.entity.User;
import com.jfj_backend.JFJ.enums.PayType;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.repository.ApplicationRepository;
import com.jfj_backend.JFJ.repository.CategoryRepository;
import com.jfj_backend.JFJ.repository.GigRepository;
import com.jfj_backend.JFJ.repository.UserRepository;
import com.jfj_backend.JFJ.service.ApplicationService;
import com.jfj_backend.JFJ.util.AgeUtils;
import com.jfj_backend.JFJ.util.GeoUtils;

/**
 * NOT @Transactional: each hire must run in its own real transaction on its own thread, exactly as
 * parallel HTTP requests would. So this class commits real rows and cleans them up itself.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HiringConcurrencyTests {

	@Autowired
	ApplicationService applicationService;

	@Autowired
	UserRepository users;

	@Autowired
	GigRepository gigs;

	@Autowired
	ApplicationRepository applications;

	@Autowired
	CategoryRepository categories;

	@Autowired
	JdbcTemplate jdbc;

	// Other test classes roll back, so at this point the only rows are ours: wipe users and everything
	// that points at them (CASCADE), before and after.
	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.execute("TRUNCATE users CASCADE");
	}

	@Test
	void eightSimultaneousHiresFillExactlyThreePlaces() throws Exception {
		User provider = users.save(new User("Meena", "meena@example.com", "9678901234", "$2a$10$hash",
				LocalDate.of(1985, 1, 1), "1 Road", "Guwahati", "Assam", "781001"));
		Gig gig = new Gig(provider, GeoUtils.point(26.1445, 91.7362), new SplittableRandom(1));
		gig.updateDetails(categories.findAll().getFirst(), "Wedding helpers", "Serving food", PayType.DAILY,
				new BigDecimal("500"), 3, null, AgeUtils.todayInIndia().plusDays(5), "1 day", null, "a", "b", "c",
				Set.of());
		gig.publish();
		gigs.save(gig);

		int taps = 8;
		List<UUID> applicationIds = new ArrayList<>();
		for (int i = 0; i < taps; i++) {
			User worker = users.save(new User("Worker " + i, "w" + i + "@example.com", "900000000" + i,
					"$2a$10$hash", LocalDate.of(1995, 1, 1), "1 Road", "Guwahati", "Assam", "781001"));
			applicationIds.add(applications.save(new Application(gig, worker, null)).getId());
		}

		// All threads wait at the gate, then go at once: the closest we can get to simultaneous taps.
		CountDownLatch gate = new CountDownLatch(1);
		AtomicInteger hired = new AtomicInteger();
		AtomicInteger refused = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(taps);
		List<Future<?>> results = new ArrayList<>();
		for (UUID id : applicationIds) {
			results.add(pool.submit(() -> {
				gate.await();
				try {
					applicationService.hire(provider.getId(), id);
					hired.incrementAndGet();
				}
				catch (BadRequestException ex) {
					refused.incrementAndGet();
				}
				return null;
			}));
		}
		gate.countDown();
		for (Future<?> result : results) {
			result.get();   // rethrows anything unexpected
		}
		pool.shutdown();

		assertThat(hired.get()).isEqualTo(3);
		assertThat(refused.get()).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM assignments WHERE gig_id = ?", Integer.class,
				gig.getId())).isEqualTo(3);
	}
}
