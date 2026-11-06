package com.jfj_backend.JFJ.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

/** The filters list and map share (FIND-4: "shares filters with list view"). Any of them may be null. */
public record GigFilters(Long categoryId, BigDecimal minPay, BigDecimal maxPay, PayType payType,
		ExperienceLevel experience, LocalDate startFrom, String q) {
}
