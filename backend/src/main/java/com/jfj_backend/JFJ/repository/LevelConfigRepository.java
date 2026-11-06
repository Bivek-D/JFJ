package com.jfj_backend.JFJ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.LevelConfig;

public interface LevelConfigRepository extends JpaRepository<LevelConfig, Integer> {

	List<LevelConfig> findAllByOrderByMinXpDesc();
}
