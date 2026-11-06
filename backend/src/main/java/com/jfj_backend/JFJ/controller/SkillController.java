package com.jfj_backend.JFJ.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jfj_backend.JFJ.dto.response.SkillResponse;
import com.jfj_backend.JFJ.service.SkillService;

/** Public: the signup/profile screens and gig forms need the list before or without a login. */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

	private final SkillService skillService;

	public SkillController(SkillService skillService) {
		this.skillService = skillService;
	}

	@GetMapping
	public List<SkillResponse> list() {
		return skillService.listAll();
	}
}
