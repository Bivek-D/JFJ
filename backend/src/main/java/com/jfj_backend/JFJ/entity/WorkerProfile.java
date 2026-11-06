package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.jfj_backend.JFJ.enums.ExperienceLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * One row of worker_profiles (V3). Shares its id with the user: the primary key is user_id.
 * profileComplete is recalculated by every method that changes a field it depends on.
 */
@Entity
@Table(name = "worker_profiles")
public class WorkerProfile {

	@Id
	private UUID userId;

	// @MapsId: the id above is taken from this user, so there is no separate id column.
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(length = 500)
	private String photoUrl;

	@Column(length = 1000)
	private String description;

	// A Postgres array (TEXT[]): languages are just labels, so a separate table isn't worth it.
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> languages = new ArrayList<>();

	@Column(nullable = false)
	private int experienceYears;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private ExperienceLevel experienceLevel = ExperienceLevel.BEGINNER;

	@Column(nullable = false)
	private boolean profileComplete;

	// Many workers have many skills: rows in worker_skills link the two. A Set ignores duplicates.
	@ManyToMany
	@JoinTable(name = "worker_skills",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "skill_id"))
	private Set<Skill> skills = new HashSet<>();

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected WorkerProfile() {
	}

	public WorkerProfile(User user) {
		this.user = user;
	}

	/** PUT /workers/me/profile replaces all editable fields at once. */
	public void updateDetails(String photoUrl, String description, List<String> languages, int experienceYears,
			ExperienceLevel experienceLevel) {
		this.photoUrl = photoUrl;
		this.description = description;
		this.languages = new ArrayList<>(languages);
		this.experienceYears = experienceYears;
		this.experienceLevel = experienceLevel;
		refreshCompleteness();
	}

	/** PUT /workers/me/skills replaces the whole set. */
	public void replaceSkills(Set<Skill> newSkills) {
		this.skills.clear();
		this.skills.addAll(newSkills);
		refreshCompleteness();
	}

	/** The PRD's rule for "complete": a photo, at least one skill and at least one language. */
	private void refreshCompleteness() {
		this.profileComplete = photoUrl != null && !skills.isEmpty() && !languages.isEmpty();
	}

	public UUID getUserId() {
		return userId;
	}

	public User getUser() {
		return user;
	}

	public String getPhotoUrl() {
		return photoUrl;
	}

	public String getDescription() {
		return description;
	}

	public List<String> getLanguages() {
		return List.copyOf(languages);
	}

	public int getExperienceYears() {
		return experienceYears;
	}

	public ExperienceLevel getExperienceLevel() {
		return experienceLevel;
	}

	public boolean isProfileComplete() {
		return profileComplete;
	}

	public Set<Skill> getSkills() {
		return Set.copyOf(skills);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
