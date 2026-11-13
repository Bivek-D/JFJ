package com.jfj_backend.JFJ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, Long> {

	List<Skill> findAllByOrderByNameAsc();
}
