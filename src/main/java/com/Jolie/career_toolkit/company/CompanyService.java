package com.Jolie.career_toolkit.company;

import com.Jolie.career_toolkit.common.ResourceNotFoundException;
import com.Jolie.career_toolkit.company.dto.CreateCompanyRequest;
import com.Jolie.career_toolkit.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公司是全域共用的目錄，任何登入者都能讀、都能新增。
 * 修改與標記 verified 才需要管理員。
 */
@Service
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final IndustryRepository industryRepository;
    private final CurrentUser currentUser;

    public CompanyService(CompanyRepository companyRepository,
                          IndustryRepository industryRepository,
                          CurrentUser currentUser) {
        this.companyRepository = companyRepository;
        this.industryRepository = industryRepository;
        this.currentUser = currentUser;
    }

    public List<Industry> listIndustries() {
        return industryRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public List<Company> list(String search) {
        return (search == null || search.isBlank())
                ? companyRepository.findAllByOrderByNameAsc()
                : companyRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
    }

    public Company get(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }

    /**
     * 建立公司。同名的話直接回傳既有那一筆，不報錯。
     *
     * 理由：使用者要建投遞紀錄時才會順手建公司，這時跳出「公司已存在」的錯誤
     * 只會讓人卡住——他要的是「把這筆投遞掛到台積電底下」，不在乎是不是他建的。
     * 重複資料的合併留給管理員處理。
     */
    @Transactional
    public Company findOrCreate(CreateCompanyRequest request) {
        return companyRepository.findByNameIgnoringCase(request.name().trim())
                .orElseGet(() -> {
                    Company company = new Company(
                            request.name().trim(), request.industryId(), currentUser.id());
                    company.setWebsite(request.website());
                    company.setNotes(request.notes());
                    return companyRepository.save(company);
                });
    }

    /** 一次撈完要用到的公司，避免列表頁面一筆一筆查（N+1）。 */
    public Map<UUID, Company> findAllByIds(Collection<UUID> ids) {
        Set<UUID> distinct = Set.copyOf(ids);
        if (distinct.isEmpty()) return Map.of();

        return companyRepository.findAllById(distinct).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    public Map<Short, Industry> industriesById() {
        return industryRepository.findAll().stream()
                .collect(Collectors.toMap(Industry::getId, Function.identity()));
    }
}
