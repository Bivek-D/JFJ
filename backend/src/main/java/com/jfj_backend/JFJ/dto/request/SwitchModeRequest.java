package com.jfj_backend.JFJ.dto.request;

import com.jfj_backend.JFJ.enums.Mode;

import jakarta.validation.constraints.NotNull;

public record SwitchModeRequest(@NotNull Mode mode) {
}
