package com.Jolie.career_toolkit.answers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 全站共用的參照表，跟使用者無關。 */
public interface QuestionTypeRepository extends JpaRepository<QuestionType, Short> {

    List<QuestionType> findByActiveTrueOrderBySortOrderAsc();
}
