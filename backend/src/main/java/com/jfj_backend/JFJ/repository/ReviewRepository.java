package com.jfj_backend.JFJ.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	boolean existsByAssignmentIdAndReviewerId(UUID assignmentId, UUID reviewerId);

	Page<Review> findByRevieweeId(UUID revieweeId, Pageable pageable);

	List<Review> findByAssignmentIdIn(List<UUID> assignmentIds);
}
