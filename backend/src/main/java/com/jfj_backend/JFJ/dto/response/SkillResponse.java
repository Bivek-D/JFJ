package com.jfj_backend.JFJ.dto.response;

import com.jfj_backend.JFJ.entity.Skill;

public record SkillResponse(Long id, String name) {

	public static SkillResponse from(Skill skill) {
		return new SkillResponse(skill.getId(), skill.getName());
	}
}
