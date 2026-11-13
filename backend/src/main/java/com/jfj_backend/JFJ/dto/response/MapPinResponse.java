package com.jfj_backend.JFJ.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.jfj_backend.JFJ.enums.PayType;

/** lat/lng are the approximate point, never the exact one. */
public record MapPinResponse(UUID id, String title, BigDecimal payAmount, PayType payType, Long categoryId,
		double lat, double lng) {
}
