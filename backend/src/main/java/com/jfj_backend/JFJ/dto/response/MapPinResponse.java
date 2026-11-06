package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.jfj_backend.JFJ.enums.PayType;

/** One pin on the map (MAP-2): only what the pin and its mini card need. lat/lng are approximate. */
public record MapPinResponse(UUID id, String title, BigDecimal payAmount, PayType payType, Long categoryId,
		double lat, double lng) {
}
