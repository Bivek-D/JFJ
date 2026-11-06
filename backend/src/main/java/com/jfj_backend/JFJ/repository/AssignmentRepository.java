package com.jfj_backend.JFJ.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Assignment;
import com.jfj_backend.JFJ.enums.AssignmentStatus;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

	long countByGigIdAndStatus(UUID gigId, AssignmentStatus status);

	boolean existsByGigId(UUID gigId);

	boolean existsByGigIdAndWorkerIdAndStatus(UUID gigId, UUID workerId, AssignmentStatus status);

	Optional<Assignment> findByApplicationId(UUID applicationId);

	List<Assignment> findByApplicationIdIn(List<UUID> applicationIds);
}
