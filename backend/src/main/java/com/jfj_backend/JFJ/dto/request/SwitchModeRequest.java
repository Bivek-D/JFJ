package com.jfj_backend.JFJ.dto.request;

import com.jfj_backend.JFJ.enums.Mode;

import jakarta.validation.constraints.NotNull;

/** Body of POST /api/v1/auth/switch-mode: {"mode": "PROVIDER"} or {"mode": "WORKER"}. */
public record SwitchModeRequest(@NotNull Mode mode) {
}
