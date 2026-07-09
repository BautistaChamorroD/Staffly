package com.staffly.backend.company.dto;

public record CreateCompanyResponse(
        CompanyResponse company,
        String adminEmail,
        String adminTemporaryPassword) {
}
