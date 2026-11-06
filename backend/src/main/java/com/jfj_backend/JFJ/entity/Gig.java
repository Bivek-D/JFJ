package com.jfj_backend.JFJ.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.GigStatus;
import com.jfj_backend.JFJ.enums.PayType;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.util.GeoUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One row of gigs (V4). The lifecycle and location rules live here, next to the data they protect:
 * there are no plain setters, only methods that check the rule first.
 * search_vector is not mapped: Postgres fills it, and Day 7's search reads it with native SQL.
 */
@Entity
@Table(name = "gigs")
public class Gig {

	public static final int MAX_LOCATION_EDITS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id")
	private User provider;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PayType payType;

	// BigDecimal, never double, for money: 0.1 + 0.2 is 0.30000000000000004 in double.
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal payAmount;

	@Column(nullable = false)
	private int workersNeeded;

	@Enumerated(EnumType.STRING)
	@Column(length = 15)
	private ExperienceLevel minExperience;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false, length = 100)
	private String durationText;

	private LocalDate applyDeadline;

	@Column(nullable = false)
	private String addressLine;

	@Column(nullable = false, length = 100)
	private String locality;

	@Column(nullable = false, length = 100)
	private String city;

	// Hibernate Spatial maps PostGIS geometry to a JTS Point (x = longitude, y = latitude).
	@Column(nullable = false, columnDefinition = "geometry(Point,4326)")
	private Point location;

	@Column(nullable = false, columnDefinition = "geometry(Point,4326)")
	private Point publicLocation;

	@Column(nullable = false)
	private int locationEditCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private GigStatus status = GigStatus.DRAFT;

	@ManyToMany
	@JoinTable(name = "gig_skills",
			joinColumns = @JoinColumn(name = "gig_id"),
			inverseJoinColumns = @JoinColumn(name = "skill_id"))
	private Set<Skill> skills = new HashSet<>();

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected Gig() {
	}

	/** A new gig starts as DRAFT (PRD 4.4); creating it sets the first location, which is not an "edit". */
	public Gig(User provider, Point exactLocation, RandomGenerator random) {
		this.provider = provider;
		this.location = exactLocation;
		this.publicLocation = GeoUtils.approximate(exactLocation, random);
	}

	/** Everything except the location. Allowed in DRAFT, and in OPEN while nobody is hired. */
	public void updateDetails(Category category, String title, String description, PayType payType,
			BigDecimal payAmount, int workersNeeded, ExperienceLevel minExperience, LocalDate startDate,
			String durationText, LocalDate applyDeadline, String addressLine, String locality, String city,
			Set<Skill> skills) {
		requireEditable();
		this.category = category;
		this.title = title;
		this.description = description;
		this.payType = payType;
		this.payAmount = payAmount;
		this.workersNeeded = workersNeeded;
		this.minExperience = minExperience;
		this.startDate = startDate;
		this.durationText = durationText;
		this.applyDeadline = applyDeadline;
		this.addressLine = addressLine;
		this.locality = locality;
		this.city = city;
		this.skills.clear();
		this.skills.addAll(skills);
	}

	/**
	 * Move the pin (GIG-6): at most 3 times per gig. A new public point is made only when the exact
	 * point really changes, so saving the form without moving the pin costs nothing.
	 */
	public void moveTo(Point newExact, RandomGenerator random) {
		requireEditable();
		if (newExact.equalsExact(location)) {
			return;
		}
		if (locationEditCount >= MAX_LOCATION_EDITS) {
			throw new BadRequestException("The location can be changed at most " + MAX_LOCATION_EDITS + " times");
		}
		// Not yet: location is also locked after the first hire (B4).
		this.location = newExact;
		this.publicLocation = GeoUtils.approximate(newExact, random);
		this.locationEditCount++;
	}

	/** DRAFT -> OPEN. Workers can see and apply to it from now on. */
	public void publish() {
		if (status != GigStatus.DRAFT) {
			throw new BadRequestException("Only a draft can be published");
		}
		this.status = GigStatus.OPEN;
	}

	public boolean isDraft() {
		return status == GigStatus.DRAFT;
	}

	public boolean isOwnedBy(UUID userId) {
		return provider.getId().equals(userId);
	}

	// Not yet: OPEN stops being editable once a worker is hired (B4 adds assignments).
	private void requireEditable() {
		if (status != GigStatus.DRAFT && status != GigStatus.OPEN) {
			throw new BadRequestException("This gig can no longer be edited");
		}
	}

	public UUID getId() {
		return id;
	}

	public User getProvider() {
		return provider;
	}

	public Category getCategory() {
		return category;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public PayType getPayType() {
		return payType;
	}

	public BigDecimal getPayAmount() {
		return payAmount;
	}

	public int getWorkersNeeded() {
		return workersNeeded;
	}

	public ExperienceLevel getMinExperience() {
		return minExperience;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public String getDurationText() {
		return durationText;
	}

	public LocalDate getApplyDeadline() {
		return applyDeadline;
	}

	public String getAddressLine() {
		return addressLine;
	}

	public String getLocality() {
		return locality;
	}

	public String getCity() {
		return city;
	}

	public Point getLocation() {
		return location;
	}

	public Point getPublicLocation() {
		return publicLocation;
	}

	public int getLocationEditCount() {
		return locationEditCount;
	}

	public GigStatus getStatus() {
		return status;
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
