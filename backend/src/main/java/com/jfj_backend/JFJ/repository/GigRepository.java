package com.jfj_backend.JFJ.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.GigStatus;

import jakarta.persistence.LockModeType;

public interface GigRepository extends JpaRepository<Gig, UUID> {

	/**
	 * Loads the gig AND locks its row until this transaction ends (SELECT ... FOR UPDATE).
	 * A second transaction asking for the same lock waits in line, then sees the first one's changes.
	 * Used by hiring, so two hires at once can't both take the last opening.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Gig g where g.id = :id")
	Optional<Gig> findByIdForUpdate(UUID id);

	// GET /gigs/mine. Pageable adds LIMIT/OFFSET + ORDER BY, and Page also runs a COUNT for the total.
	Page<Gig> findByProviderId(UUID providerId, Pageable pageable);

	Page<Gig> findByProviderIdAndStatus(UUID providerId, GigStatus status, Pageable pageable);
}
