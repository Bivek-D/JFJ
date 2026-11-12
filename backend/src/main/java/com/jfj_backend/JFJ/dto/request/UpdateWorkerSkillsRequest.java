package com.jfj_backend.JFJ.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkerSkillsRequest(@NotNull @Size(max = 10) Set<@NotNull Long> skillIds) {
}
