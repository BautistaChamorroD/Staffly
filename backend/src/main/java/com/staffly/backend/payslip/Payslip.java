package com.staffly.backend.payslip;

import com.staffly.backend.employee.Employee;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.decorator.DeduccionLinea;
import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payslip")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Payslip extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoRecibo estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoRecibo tipo;

    @Column(name = "payslip_original_id")
    private UUID payslipOriginalId;

    // ── snapshot del cálculo ──────────────────────────────────────────────────

    @Column(name = "sueldo_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal sueldoBase;

    @Column(name = "horas_normales", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasNormales;

    @Column(name = "horas_extra", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasExtra;

    @Column(name = "horas_feriado", nullable = false, precision = 10, scale = 2)
    private BigDecimal horasFeriado;

    @Column(name = "monto_horas_normales", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoHorasNormales;

    @Column(name = "monto_horas_extra", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoHorasExtra;

    @Column(name = "monto_horas_feriado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoHorasFeriado;

    @Column(name = "bruto_calculado", nullable = false, precision = 14, scale = 2)
    private BigDecimal brutoCalculado;

    @Column(name = "total_deducciones", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeducciones;

    @Column(name = "total_adelantos", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAdelantos;

    @Column(name = "neto_final", nullable = false, precision = 14, scale = 2)
    private BigDecimal netoFinal;

    @Convert(converter = DeduccionLineaListConverter.class)
    @Column(name = "detalle_descuentos", nullable = false, columnDefinition = "TEXT")
    private List<DeduccionLinea> detalleDescuentos;

    @Convert(converter = UUIDListConverter.class)
    @Column(name = "adelantos_aplicados", nullable = false, columnDefinition = "TEXT")
    private List<UUID> adelantosAplicados;

    // ── campos de ciclo de vida ───────────────────────────────────────────────

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "motivo_anulacion", length = 1000)
    private String motivoAnulacion;

    // ── getters / setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public UUID getEmployeeId() { return employee != null ? employee.getId() : null; }

    public PayrollPeriod getPayrollPeriod() { return payrollPeriod; }
    public void setPayrollPeriod(PayrollPeriod payrollPeriod) { this.payrollPeriod = payrollPeriod; }

    public EstadoRecibo getEstado() { return estado; }
    public void setEstado(EstadoRecibo estado) { this.estado = estado; }

    public TipoRecibo getTipo() { return tipo; }
    public void setTipo(TipoRecibo tipo) { this.tipo = tipo; }

    public UUID getPayslipOriginalId() { return payslipOriginalId; }
    public void setPayslipOriginalId(UUID payslipOriginalId) { this.payslipOriginalId = payslipOriginalId; }

    public BigDecimal getSueldoBase() { return sueldoBase; }
    public void setSueldoBase(BigDecimal sueldoBase) { this.sueldoBase = sueldoBase; }

    public BigDecimal getHorasNormales() { return horasNormales; }
    public void setHorasNormales(BigDecimal horasNormales) { this.horasNormales = horasNormales; }

    public BigDecimal getHorasExtra() { return horasExtra; }
    public void setHorasExtra(BigDecimal horasExtra) { this.horasExtra = horasExtra; }

    public BigDecimal getHorasFeriado() { return horasFeriado; }
    public void setHorasFeriado(BigDecimal horasFeriado) { this.horasFeriado = horasFeriado; }

    public BigDecimal getMontoHorasNormales() { return montoHorasNormales; }
    public void setMontoHorasNormales(BigDecimal montoHorasNormales) { this.montoHorasNormales = montoHorasNormales; }

    public BigDecimal getMontoHorasExtra() { return montoHorasExtra; }
    public void setMontoHorasExtra(BigDecimal montoHorasExtra) { this.montoHorasExtra = montoHorasExtra; }

    public BigDecimal getMontoHorasFeriado() { return montoHorasFeriado; }
    public void setMontoHorasFeriado(BigDecimal montoHorasFeriado) { this.montoHorasFeriado = montoHorasFeriado; }

    public BigDecimal getBrutoCalculado() { return brutoCalculado; }
    public void setBrutoCalculado(BigDecimal brutoCalculado) { this.brutoCalculado = brutoCalculado; }

    public BigDecimal getTotalDeducciones() { return totalDeducciones; }
    public void setTotalDeducciones(BigDecimal totalDeducciones) { this.totalDeducciones = totalDeducciones; }

    public BigDecimal getTotalAdelantos() { return totalAdelantos; }
    public void setTotalAdelantos(BigDecimal totalAdelantos) { this.totalAdelantos = totalAdelantos; }

    public BigDecimal getNetoFinal() { return netoFinal; }
    public void setNetoFinal(BigDecimal netoFinal) { this.netoFinal = netoFinal; }

    public List<DeduccionLinea> getDetalleDescuentos() { return detalleDescuentos; }
    public void setDetalleDescuentos(List<DeduccionLinea> detalleDescuentos) { this.detalleDescuentos = detalleDescuentos; }

    public List<UUID> getAdelantosAplicados() { return adelantosAplicados; }
    public void setAdelantosAplicados(List<UUID> adelantosAplicados) { this.adelantosAplicados = adelantosAplicados; }

    public LocalDate getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDate fechaPago) { this.fechaPago = fechaPago; }

    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
}
