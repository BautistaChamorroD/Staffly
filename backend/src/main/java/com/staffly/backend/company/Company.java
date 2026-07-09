package com.staffly.backend.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad raíz del modelo multi-tenant. A diferencia del resto de las
 * entidades de negocio, Company NO extiende TenantAwareEntity: es la
 * empresa (el tenant) en sí misma, no algo que pertenezca a una.
 */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(name = "pais", nullable = false)
    private String pais;

    @Column(name = "moneda", nullable = false)
    private String moneda;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoEmpresa estado;

    @Column(name = "plan")
    private String plan;

    @Column(name = "fecha_alta", nullable = false)
    private Instant fechaAlta;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (fechaAlta == null) {
            fechaAlta = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public void setZonaHoraria(String zonaHoraria) {
        this.zonaHoraria = zonaHoraria;
    }

    public EstadoEmpresa getEstado() {
        return estado;
    }

    public void setEstado(EstadoEmpresa estado) {
        this.estado = estado;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }
}
