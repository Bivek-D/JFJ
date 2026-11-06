package com.jfj_backend.JFJ.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jfj_backend.JFJ.entity.Application;
import com.jfj_backend.JFJ.enums.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

	boolean existsByGigIdAndWorkerId(UUID gigId, UUID workerId);

	Page<Application> findByWorkerId(UUID workerId, Pageable pageable);

	List<Application> findByGigIdOrderByCreatedAtDesc(UUID gigId);

	List<Application> findByGigIdAndStatusOrderByCreatedAtDesc(UUID gigId, ApplicationStatus status);

	List<Application> findByGigIdAndStatusIn(UUID gigId, List<ApplicationStatus> statuses);

	/** Only the gig id, without loading the application, so hiring can lock the gig FIRST (see ApplicationService.hire). */
	@Query("select a.gig.id from Application a where a.id = :id")
	Optional<UUID> findGigIdById(UUID id);
}
