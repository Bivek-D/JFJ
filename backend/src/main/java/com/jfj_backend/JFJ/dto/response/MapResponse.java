package com.jfj_backend.JFJ.dto.response;

import java.util.List;

/** truncated = more than 200 gigs are in the area; the app asks the user to zoom in. */
public record MapResponse(List<MapPinResponse> items, boolean truncated) {
}
