package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jfj_backend.JFJ.enums.AssignmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** One row of assignments (V5): a hired worker on a gig. Day 10 adds complete and remove. */
@Entity
@Table(name = "assignments")
public class Assignment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "gig_id")
	private Gig gig;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "worker_id")
	private User worker;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id")
	private Application application;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AssignmentStatus status = AssignmentStatus.ACTIVE;

	@Column(length = 500)
	private String removedReason;

	private Instant endedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected Assignment() {
	}

	public Assignment(Application application) {
		this.application = application;
		this.gig = application.getGig();
		this.worker = application.getWorker();
	}

	public UUID getId() {
		return id;
	}

	public Gig getGig() {
		return gig;
	}

	public User getWorker() {
		return worker;
	}

	public Application getApplication() {
		return application;
	}

	public AssignmentStatus getStatus() {
		return status;
	}

	public String getRemovedReason() {
		return removedReason;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
