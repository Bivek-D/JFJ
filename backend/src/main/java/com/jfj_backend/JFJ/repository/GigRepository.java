package com.jfj_backend.JFJ.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Gig;
import com.jfj_backend.JFJ.enums.GigStatus;

public interface GigRepository extends JpaRepository<Gig, UUID> {

	// GET /gigs/mine. Pageable adds LIMIT/OFFSET + ORDER BY, and Page also runs a COUNT for the total.
	Page<Gig> findByProviderId(UUID providerId, Pageable pageable);

	Page<Gig> findByProviderIdAndStatus(UUID providerId, GigStatus status, Pageable pageable);
}
