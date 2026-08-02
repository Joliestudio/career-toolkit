package com.Jolie.career_toolkit.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** industries 是全站共用的參照表，跟使用者無關。 */
public interface IndustryRepository extends JpaRepository<Industry, Short> {

    List<Industry> findByActiveTrueOrderBySortOrderAsc();

    Optional<Industry> findByCode(String code);
}
