package com.jfj_backend.JFJ.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of level_config (V7): level number, name, XP needed. Read-only for the app. */
@Entity
@Table(name = "level_config")
public class LevelConfig {

	@Id
	private Integer level;

	@Column(nullable = false, unique = true, length = 30)
	private String name;

	@Column(nullable = false, unique = true)
	private int minXp;

	protected LevelConfig() {
	}

	public Integer getLevel() {
		return level;
	}

	public String getName() {
		return name;
	}

	public int getMinXp() {
		return minXp;
	}
}
