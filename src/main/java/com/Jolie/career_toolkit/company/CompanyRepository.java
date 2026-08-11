package com.Jolie.career_toolkit.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * companies 是全域共用的目錄，沒有 user_id 可以綁——所以這個 repository 的方法名
 * 不會含 UserId。RepositoryOwnershipConventionTest 的 EXEMPT 清單裡有寫明理由。
 */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Query("select c from Company c where lower(c.name) = lower(:name)")
    Optional<Company> findByNameIgnoringCase(String name);

    List<Company> findByNameContainingIgnoreCaseOrderByNameAsc(String fragment);

    List<Company> findAllByOrderByNameAsc();
}
