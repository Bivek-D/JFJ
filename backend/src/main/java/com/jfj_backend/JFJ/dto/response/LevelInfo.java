package com.jfj_backend.JFJ.dto.response;

/** Earned level (PRD 5.7), e.g. {level: 2, name: "Trusted", totalXp: 75}. */
public record LevelInfo(int level, String name, int totalXp) {
}
