package com.staffly.backend.company;

import com.staffly.backend.company.dto.CompanyResponse;
import com.staffly.backend.company.dto.CreateCompanyRequest;
import com.staffly.backend.company.dto.CreateCompanyResponse;
import com.staffly.backend.company.dto.UpdateCompanyRequest;
import com.staffly.backend.company.dto.UpdateCompanyStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<List<CompanyResponse>> list() {
        return ResponseEntity.ok(companyService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(companyService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CreateCompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        CreateCompanyResponse response = companyService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + response.company().id())).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CompanyResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CompanyResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateCompanyStatusRequest request) {
        return ResponseEntity.ok(companyService.updateStatus(id, request));
    }
}
