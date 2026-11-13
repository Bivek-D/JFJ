package com.jfj_backend.JFJ.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.dto.response.CategoryResponse;
import com.jfj_backend.JFJ.dto.response.SkillResponse;
import com.jfj_backend.JFJ.service.CatalogService;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/skills")
	public List<SkillResponse> skills() {
		return catalogService.listSkills();
	}

	@GetMapping("/categories")
	public List<CategoryResponse> categories() {
		return catalogService.listCategories();
	}
}
