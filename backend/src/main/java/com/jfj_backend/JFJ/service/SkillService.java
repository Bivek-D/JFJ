package com.jfj_backend.JFJ.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.SkillResponse;
import com.jfj_backend.JFJ.repository.SkillRepository;

@Service
public class SkillService {

	private final SkillRepository skills;

	public SkillService(SkillRepository skills) {
		this.skills = skills;
	}

	@Transactional(readOnly = true)
	public List<SkillResponse> listAll() {
		return skills.findAllByOrderByNameAsc().stream().map(SkillResponse::from).toList();
	}
}
