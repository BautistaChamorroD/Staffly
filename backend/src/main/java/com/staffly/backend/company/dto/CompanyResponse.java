package com.staffly.backend.company.dto;

import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String nombre,
        String razonSocial,
        String pais,
        String moneda,
        String zonaHoraria,
        EstadoEmpresa estado,
        String plan,
        Instant fechaAlta) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getNombre(),
                company.getRazonSocial(),
                company.getPais(),
                company.getMoneda(),
                company.getZonaHoraria(),
                company.getEstado(),
                company.getPlan(),
                company.getFechaAlta());
    }
}
