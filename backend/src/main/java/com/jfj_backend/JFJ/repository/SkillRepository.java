package com.jfj_backend.JFJ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, Long> {

	// Spring Data builds the query from the method name: SELECT ... ORDER BY name ASC.
	List<Skill> findAllByOrderByNameAsc();
}
