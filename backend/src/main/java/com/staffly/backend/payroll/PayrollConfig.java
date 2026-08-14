package com.staffly.backend.payroll;

import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payroll_config")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class PayrollConfig extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "umbral_horas_extra", nullable = false, precision = 10, scale = 2)
    private BigDecimal umbralHorasExtra;

    @Column(name = "multiplicador_hora_extra", nullable = false, precision = 10, scale = 4)
    private BigDecimal multiplicadorHoraExtra;

    @Column(name = "multiplicador_feriado", nullable = false, precision = 10, scale = 4)
    private BigDecimal multiplicadorFeriado;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidad", nullable = false, length = 20)
    private Periodicidad periodicidad;

    @Convert(converter = ConceptoDescuentoListConverter.class)
    @Column(name = "conceptos_descuento", nullable = false, columnDefinition = "TEXT")
    private List<ConceptoDescuento> conceptosDescuento = new ArrayList<>();

    public UUID getId() { return id; }

    public BigDecimal getUmbralHorasExtra() { return umbralHorasExtra; }
    public void setUmbralHorasExtra(BigDecimal umbralHorasExtra) { this.umbralHorasExtra = umbralHorasExtra; }

    public BigDecimal getMultiplicadorHoraExtra() { return multiplicadorHoraExtra; }
    public void setMultiplicadorHoraExtra(BigDecimal multiplicadorHoraExtra) { this.multiplicadorHoraExtra = multiplicadorHoraExtra; }

    public BigDecimal getMultiplicadorFeriado() { return multiplicadorFeriado; }
    public void setMultiplicadorFeriado(BigDecimal multiplicadorFeriado) { this.multiplicadorFeriado = multiplicadorFeriado; }

    public Periodicidad getPeriodicidad() { return periodicidad; }
    public void setPeriodicidad(Periodicidad periodicidad) { this.periodicidad = periodicidad; }

    public List<ConceptoDescuento> getConceptosDescuento() { return conceptosDescuento; }
    public void setConceptosDescuento(List<ConceptoDescuento> conceptosDescuento) {
        this.conceptosDescuento = conceptosDescuento == null ? new ArrayList<>() : conceptosDescuento;
    }
}
