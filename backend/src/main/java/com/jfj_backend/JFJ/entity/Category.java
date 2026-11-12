package com.jfj_backend.JFJ.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	// Display order: "Other" has 1000 so it always comes last.
	@Column(nullable = false)
	private int sortOrder;

	// Retired categories stay (old gigs point at them) but can't be picked for new gigs.
	@Column(nullable = false)
	private boolean active;

	protected Category() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return active;
	}
}
