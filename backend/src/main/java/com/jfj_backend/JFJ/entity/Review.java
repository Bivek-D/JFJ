package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jfj_backend.JFJ.enums.ReviewDirection;

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

/** One row of reviews (V7). The 24-hour edit rule is checked in ReviewService. */
@Entity
@Table(name = "reviews")
public class Review {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assignment_id")
	private Assignment assignment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reviewer_id")
	private User reviewer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reviewee_id")
	private User reviewee;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReviewDirection direction;

	@Column(nullable = false)
	private int rating;

	@Column(length = 500)
	private String comment;

	@Column(nullable = false)
	private int editCount;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected Review() {
	}

	public Review(Assignment assignment, User reviewer, User reviewee, ReviewDirection direction, int rating,
			String comment) {
		this.assignment = assignment;
		this.reviewer = reviewer;
		this.reviewee = reviewee;
		this.direction = direction;
		this.rating = rating;
		this.comment = comment;
	}

	/** Returns the old rating, so the caller can adjust the aggregate and XP by the difference. */
	public int edit(int newRating, String newComment) {
		int old = rating;
		this.rating = newRating;
		this.comment = newComment;
		this.editCount++;
		return old;
	}

	public UUID getId() {
		return id;
	}

	public Assignment getAssignment() {
		return assignment;
	}

	public User getReviewer() {
		return reviewer;
	}

	public User getReviewee() {
		return reviewee;
	}

	public ReviewDirection getDirection() {
		return direction;
	}

	public int getRating() {
		return rating;
	}

	public String getComment() {
		return comment;
	}

	public int getEditCount() {
		return editCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
