package com.jfj_backend.JFJ.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jfj_backend.JFJ.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	List<Category> findAllByActiveTrueOrderBySortOrderAscNameAsc();
}
