package com.Jolie.career_toolkit.company;

import com.Jolie.career_toolkit.company.dto.CompanyResponse;
import com.Jolie.career_toolkit.company.dto.CreateCompanyRequest;
import com.Jolie.career_toolkit.company.dto.IndustryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/industries")
    public List<IndustryResponse> listIndustries() {
        return companyService.listIndustries().stream().map(IndustryResponse::from).toList();
    }

    @GetMapping("/companies")
    public List<CompanyResponse> listCompanies(@RequestParam(required = false) String search) {
        Map<Short, Industry> industries = companyService.industriesById();

        return companyService.list(search).stream()
                .map(c -> CompanyResponse.from(c, industries.get(c.getIndustryId())))
                .toList();
    }

    /**
     * 同名的公司會回傳既有那一筆，所以固定回 200 而不是 201——
     * 回應的語意是「這是你要的公司」，不是「我剛剛建了一個」。
     */
    @PostMapping("/companies")
    public CompanyResponse createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        Company company = companyService.findOrCreate(request);
        Industry industry = company.getIndustryId() == null
                ? null
                : companyService.industriesById().get(company.getIndustryId());

        return CompanyResponse.from(company, industry);
    }
}
