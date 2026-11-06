package com.jfj_backend.JFJ.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.jfj_backend.JFJ.enums.XpEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of xp_events (V7). Append-only: no setters, rows are never changed or deleted. */
@Entity
@Table(name = "xp_events")
public class XpEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private XpEventType eventType;

	@Column(nullable = false)
	private int points;

	@Column(nullable = false)
	private UUID refId;

	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected XpEvent() {
	}

	public XpEvent(UUID userId, XpEventType eventType, int points, UUID refId, int version) {
		this.userId = userId;
		this.eventType = eventType;
		this.points = points;
		this.refId = refId;
		this.version = version;
	}

	public int getPoints() {
		return points;
	}

	public XpEventType getEventType() {
		return eventType;
	}
}
