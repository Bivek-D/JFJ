package com.jfj_backend.JFJ.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jfj_backend.JFJ.enums.ExperienceLevel;
import com.jfj_backend.JFJ.enums.PayType;

public record GigFilters(Long categoryId, BigDecimal minPay, BigDecimal maxPay, PayType payType,
		ExperienceLevel experience, LocalDate startFrom, String q) {
}
