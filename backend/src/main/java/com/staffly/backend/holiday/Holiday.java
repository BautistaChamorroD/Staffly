package com.staffly.backend.holiday;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holiday")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Holiday extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "branch_id", nullable = true)
    private Branch branch;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "recurrente", nullable = false)
    private boolean recurrente;

    public UUID getId() { return id; }

    public Branch getBranch() { return branch; }

    public void setBranch(Branch branch) { this.branch = branch; }

    /** Devuelve el UUID de la sucursal, o null si el feriado es global. */
    public UUID getBranchId() {
        return branch != null ? branch.getId() : null;
    }

    public LocalDate getFecha() { return fecha; }

    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getNombre() { return nombre; }

    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isRecurrente() { return recurrente; }

    public void setRecurrente(boolean recurrente) { this.recurrente = recurrente; }
}
