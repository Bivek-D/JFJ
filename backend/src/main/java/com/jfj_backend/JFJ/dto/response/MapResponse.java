package com.jfj_backend.JFJ.dto.response;

import java.util.List;

public record MapResponse(List<MapPinResponse> items, boolean truncated) {
}
