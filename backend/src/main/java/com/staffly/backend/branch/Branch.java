package com.staffly.backend.branch;

import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branch")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Branch extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "direccion", nullable = false)
    private String direccion;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoSucursal estado;

    /**
     * Rango horario que se muestra por defecto en la pantalla de armado de
     * horarios (ej. 08:00 a 02:00). Es solo una preferencia visual de UI,
     * no restringe en qué horario se puede asignar un turno.
     */
    @Column(name = "horario_visible_inicio")
    private LocalTime horarioVisibleInicio;

    @Column(name = "horario_visible_fin")
    private LocalTime horarioVisibleFin;

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public void setZonaHoraria(String zonaHoraria) {
        this.zonaHoraria = zonaHoraria;
    }

    public EstadoSucursal getEstado() {
        return estado;
    }

    public void setEstado(EstadoSucursal estado) {
        this.estado = estado;
    }

    public LocalTime getHorarioVisibleInicio() {
        return horarioVisibleInicio;
    }

    public void setHorarioVisibleInicio(LocalTime horarioVisibleInicio) {
        this.horarioVisibleInicio = horarioVisibleInicio;
    }

    public LocalTime getHorarioVisibleFin() {
        return horarioVisibleFin;
    }

    public void setHorarioVisibleFin(LocalTime horarioVisibleFin) {
        this.horarioVisibleFin = horarioVisibleFin;
    }
}
