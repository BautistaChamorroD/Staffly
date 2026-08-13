package com.staffly.backend.leave;

import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.util.UUID;

@Entity
@Table(name = "leave_type")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class LeaveType extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "es_paga", nullable = false)
    private boolean esPaga;

    @Column(name = "cupo_anual")
    private Integer cupoAnual;

    public UUID getId() { return id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isEsPaga() { return esPaga; }
    public void setEsPaga(boolean esPaga) { this.esPaga = esPaga; }

    public Integer getCupoAnual() { return cupoAnual; }
    public void setCupoAnual(Integer cupoAnual) { this.cupoAnual = cupoAnual; }
}
