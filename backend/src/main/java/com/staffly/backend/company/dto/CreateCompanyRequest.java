package com.staffly.backend.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank String nombre,
        @NotBlank String razonSocial,
        @NotBlank String pais,
        @NotBlank String moneda,
        @NotBlank String zonaHoraria,
        String plan,
        @NotBlank @Email String adminEmail) {
}
