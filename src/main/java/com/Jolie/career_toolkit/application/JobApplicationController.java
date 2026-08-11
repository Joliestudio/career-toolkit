package com.Jolie.career_toolkit.application;

import com.Jolie.career_toolkit.application.dto.*;
import com.Jolie.career_toolkit.company.Company;
import com.Jolie.career_toolkit.company.CompanyService;
import com.Jolie.career_toolkit.company.Industry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationService applicationService;
    private final CompanyService companyService;

    public JobApplicationController(JobApplicationService applicationService,
                                    CompanyService companyService) {
        this.applicationService = applicationService;
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request) {

        JobApplication application = applicationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(application.getId()).toUri();

        return ResponseEntity.created(location).body(toResponse(application));
    }

    @GetMapping
    public List<ApplicationResponse> list(@RequestParam(required = false) ApplicationStatus status) {
        List<JobApplication> applications = applicationService.list(status);

        // 一次把用到的公司撈完，不要在迴圈裡一筆一筆查（N+1）
        Map<UUID, Company> companies = companyService.findAllByIds(
                applications.stream().map(JobApplication::getCompanyId).toList());
        Map<Short, Industry> industries = companyService.industriesById();

        return applications.stream()
                .map(a -> {
                    Company company = companies.get(a.getCompanyId());
                    Industry industry = company == null || company.getIndustryId() == null
                            ? null : industries.get(company.getIndustryId());
                    return ApplicationResponse.from(a,
                            company != null ? company.getName() : null,
                            industry != null ? industry.getNameZh() : null);
                })
                .toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return toResponse(applicationService.get(id));
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return toResponse(applicationService.update(id, request));
    }

    /**
     * 狀態轉換獨立一個端點，不放在 PATCH 裡。
     *
     * 理由：改狀態跟改備註是完全不同的操作——前者有合法性檢查、會寫歷程、
     * 可能被拒絕；後者不會。混在同一個端點裡的話，
     * 「改備註順便把狀態改掉」這種請求的語意會變得很難定義。
     */
    @PostMapping("/{id}/status")
    public ApplicationResponse changeStatus(@PathVariable UUID id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return toResponse(applicationService.changeStatus(id, request.status(), request.note()));
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable UUID id) {
        return applicationService.statusHistory(id).stream()
                .map(StatusHistoryResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ApplicationResponse toResponse(JobApplication application) {
        Company company = companyService.findAllByIds(List.of(application.getCompanyId()))
                .get(application.getCompanyId());
        Industry industry = company == null || company.getIndustryId() == null
                ? null : companyService.industriesById().get(company.getIndustryId());

        return ApplicationResponse.from(application,
                company != null ? company.getName() : null,
                industry != null ? industry.getNameZh() : null);
    }
}
