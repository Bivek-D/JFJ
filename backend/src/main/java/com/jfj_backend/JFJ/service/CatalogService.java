package com.jfj_backend.JFJ.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jfj_backend.JFJ.dto.response.CategoryResponse;
import com.jfj_backend.JFJ.dto.response.SkillResponse;
import com.jfj_backend.JFJ.entity.Category;
import com.jfj_backend.JFJ.entity.Skill;
import com.jfj_backend.JFJ.exception.BadRequestException;
import com.jfj_backend.JFJ.repository.CategoryRepository;
import com.jfj_backend.JFJ.repository.SkillRepository;

/** Reference lists (skills, categories) and the checks other services use when a request names them. */
@Service
public class CatalogService {

	private final SkillRepository skills;
	private final CategoryRepository categories;

	public CatalogService(SkillRepository skills, CategoryRepository categories) {
		this.skills = skills;
		this.categories = categories;
	}

	@Transactional(readOnly = true)
	public List<SkillResponse> listSkills() {
		return skills.findAllByOrderByNameAsc().stream().map(SkillResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> listCategories() {
		return categories.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream().map(CategoryResponse::from).toList();
	}

	/** Loads the skills by id; any id that doesn't exist -> 400 naming the bad ids. */
	@Transactional(readOnly = true)
	public Set<Skill> requireSkills(Set<Long> skillIds) {
		if (skillIds == null || skillIds.isEmpty()) {
			return Set.of();
		}
		List<Skill> found = skills.findAllById(skillIds);
		if (found.size() != skillIds.size()) {
			Set<Long> unknown = new TreeSet<>(skillIds);
			found.forEach(skill -> unknown.remove(skill.getId()));
			throw new BadRequestException("Unknown skill id(s): " + unknown);
		}
		return new HashSet<>(found);
	}

	/** A category that exists and can still be picked for new or edited gigs. */
	@Transactional(readOnly = true)
	public Category requireActiveCategory(Long categoryId) {
		return categories.findById(categoryId)
				.filter(Category::isActive)
				.orElseThrow(() -> new BadRequestException("Unknown category id: " + categoryId));
	}
}
