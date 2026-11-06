package com.jfj_backend.JFJ.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.WorkerProfile;

// The id type is UUID because the profile's id is its user's id.
public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, UUID> {
}
