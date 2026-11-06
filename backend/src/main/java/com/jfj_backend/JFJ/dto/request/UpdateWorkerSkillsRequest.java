package com.jfj_backend.JFJ.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of PUT /api/v1/workers/me/skills: {"skillIds": [3, 7]}. Replaces the whole set; [] clears it. */
public record UpdateWorkerSkillsRequest(@NotNull @Size(max = 10) Set<@NotNull Long> skillIds) {
}
