package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jfj_backend.JFJ.enums.ApplicationStatus;
import com.jfj_backend.JFJ.exception.BadRequestException;

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
import jakarta.persistence.Table;

/** One row of applications (V5). State changes go through methods that check the allowed moves. */
@Entity
@Table(name = "applications")
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "gig_id")
	private Gig gig;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "worker_id")
	private User worker;

	@Column(length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private ApplicationStatus status = ApplicationStatus.APPLIED;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected Application() {
	}

	public Application(Gig gig, User worker, String note) {
		this.gig = gig;
		this.worker = worker;
		this.note = note;
	}

	public void shortlist() {
		if (status != ApplicationStatus.APPLIED) {
			throw new BadRequestException("Only a new application can be shortlisted");
		}
		status = ApplicationStatus.SHORTLISTED;
	}

	public void reject() {
		requirePending("rejected");
		status = ApplicationStatus.REJECTED;
	}

	public void withdraw() {
		requirePending("withdrawn");
		status = ApplicationStatus.WITHDRAWN;
	}

	public void markHired() {
		requirePending("hired");
		status = ApplicationStatus.HIRED;
	}

	private void requirePending(String action) {
		if (!status.isPending()) {
			throw new BadRequestException("This application is " + status.name().toLowerCase()
					+ " and can't be " + action);
		}
	}

	public boolean isFrom(UUID workerId) {
		return worker.getId().equals(workerId);
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

	public String getNote() {
		return note;
	}

	public ApplicationStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
