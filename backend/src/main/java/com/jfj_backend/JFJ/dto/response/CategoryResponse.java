package com.jfj_backend.JFJ.dto.response;

import com.jfj_backend.JFJ.entity.Category;

public record CategoryResponse(Long id, String name) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName());
	}
}
