package com.staffly.backend.company.dto;

/**
 * Actualización parcial: los campos nulos se dejan sin tocar. El estado se
 * cambia únicamente vía PATCH /companies/{id}/status, no acá.
 */
public record UpdateCompanyRequest(
        String nombre,
        String razonSocial,
        String pais,
        String moneda,
        String zonaHoraria,
        String plan) {
}
