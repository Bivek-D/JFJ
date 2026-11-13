package com.jfj_backend.JFJ.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jfj_backend.JFJ.entity.XpEvent;
import com.jfj_backend.JFJ.enums.XpEventType;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

	boolean existsByUserIdAndEventTypeAndRefIdAndVersion(UUID userId, XpEventType eventType, UUID refId, int version);

	/** Source of truth: users.total_xp must always equal this sum. */
	@Query("select coalesce(sum(e.points), 0) from XpEvent e where e.userId = :userId")
	long sumPoints(UUID userId);
}
