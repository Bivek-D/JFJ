package com.jfj_backend.JFJ.dto.request;

import jakarta.validation.constraints.Size;

public record ApplyRequest(@Size(max = 500) String note) {
}
