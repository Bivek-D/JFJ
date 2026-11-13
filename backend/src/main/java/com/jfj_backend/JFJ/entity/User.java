package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.jfj_backend.JFJ.enums.Role;
import com.jfj_backend.JFJ.enums.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	// Stored lowercase by the service, so the UNIQUE constraint catches "A@x.com" vs "a@x.com".
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, unique = true, length = 10)
	private String phone;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false)
	private LocalDate dateOfBirth;

	@Column(length = 20)
	private String gender;

	@Column(nullable = false)
	private String addressLine;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(nullable = false, length = 100)
	private String state;

	@Column(nullable = false, length = 6)
	private String pincode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Role role = Role.USER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(nullable = false)
	private boolean emailVerified;

	@Column(nullable = false)
	private boolean phoneVerified;

	// Rating aggregates (V7), per role. Average = sum / count, worked out when read (never stored, never stale).
	@Column(nullable = false)
	private int ratingSum;

	@Column(nullable = false)
	private int ratingCount;

	@Column(nullable = false)
	private int providerRatingSum;

	@Column(nullable = false)
	private int providerRatingCount;

	// Cache of SUM(xp_events.points); changed only by XpService, which also writes the event.
	@Column(nullable = false)
	private int totalXp;

	@Column(nullable = false)
	private int level = 1;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String name, String email, String phone, String passwordHash, LocalDate dateOfBirth,
			String addressLine, String city, String state, String pincode) {
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.dateOfBirth = dateOfBirth;
		this.addressLine = addressLine;
		this.city = city;
		this.state = state;
		this.pincode = pincode;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getAddressLine() {
		return addressLine;
	}

	public void setAddressLine(String addressLine) {
		this.addressLine = addressLine;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getPincode() {
		return pincode;
	}

	public void setPincode(String pincode) {
		this.pincode = pincode;
	}

	public Role getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public boolean isPhoneVerified() {
		return phoneVerified;
	}

	public void addWorkerRating(int sumDelta, int countDelta) {
		this.ratingSum += sumDelta;
		this.ratingCount += countDelta;
	}

	public void addProviderRating(int sumDelta, int countDelta) {
		this.providerRatingSum += sumDelta;
		this.providerRatingCount += countDelta;
	}

	/** Only XpService calls this, right after writing the matching xp_events row. */
	public void applyXp(int points, int newLevel) {
		this.totalXp += points;
		this.level = newLevel;
	}

	public int getRatingSum() {
		return ratingSum;
	}

	public int getRatingCount() {
		return ratingCount;
	}

	public int getProviderRatingSum() {
		return providerRatingSum;
	}

	public int getProviderRatingCount() {
		return providerRatingCount;
	}

	public int getTotalXp() {
		return totalXp;
	}

	public int getLevel() {
		return level;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
