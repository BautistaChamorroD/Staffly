package com.staffly.backend.payslip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    @Query("SELECT p FROM Payslip p JOIN FETCH p.employee JOIN FETCH p.payrollPeriod WHERE p.companyId = :companyId ORDER BY p.payrollPeriod.fechaInicio DESC")
    List<Payslip> findByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT p FROM Payslip p JOIN FETCH p.employee JOIN FETCH p.payrollPeriod WHERE p.companyId = :companyId AND p.employee.id = :employeeId ORDER BY p.payrollPeriod.fechaInicio DESC")
    List<Payslip> findByCompanyIdAndEmployeeId(@Param("companyId") UUID companyId, @Param("employeeId") UUID employeeId);

    @Query("SELECT p FROM Payslip p JOIN FETCH p.employee JOIN FETCH p.payrollPeriod WHERE p.id = :id AND p.companyId = :companyId")
    Optional<Payslip> findByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByPayrollPeriodIdAndCompanyId(UUID payrollPeriodId, UUID companyId);

    @Query("SELECT p FROM Payslip p WHERE p.companyId = :companyId AND p.payrollPeriod.id = :periodId")
    List<Payslip> findByCompanyIdAndPayrollPeriodId(@Param("companyId") UUID companyId, @Param("periodId") UUID periodId);

    /**
     * Recibo NORMAL existente para este empleado+período, si lo hay — usado al
     * cerrar para actualizar en vez de insertar un duplicado (ej. al volver a
     * cerrar tras una reapertura). Excluye AJUSTE a propósito: un ajuste convive
     * legítimamente con el original (ANULADO) del mismo empleado+período.
     */
    @Query("SELECT p FROM Payslip p WHERE p.companyId = :companyId AND p.employee.id = :employeeId " +
           "AND p.payrollPeriod.id = :periodId AND p.tipo = com.staffly.backend.payslip.TipoRecibo.NORMAL")
    Optional<Payslip> findNormalByCompanyIdAndEmployeeIdAndPayrollPeriodId(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("periodId") UUID periodId);

    /**
     * Recibos de períodos CERRADOS para el reporte de costo de nómina
     * (AUD-31 / issue #152) — excluye ANULADO a propósito (superseded por un
     * AJUSTE, ver {@link #findNormalByCompanyIdAndEmployeeIdAndPayrollPeriodId}).
     * desde/hasta son opcionales vía el mismo patrón `(:param IS NULL OR ...)`
     * usado en {@code ScheduleRepository.findCumplidosForReport}, filtrando por
     * solapamiento con el rango del período.
     */
    @Query("SELECT p FROM Payslip p JOIN FETCH p.employee JOIN FETCH p.payrollPeriod pp " +
           "WHERE p.companyId = :companyId " +
           "AND pp.estado = com.staffly.backend.payroll.EstadoPeriodo.CERRADO " +
           "AND p.estado <> com.staffly.backend.payslip.EstadoRecibo.ANULADO " +
           "AND (:desde IS NULL OR pp.fechaFin >= :desde) " +
           "AND (:hasta IS NULL OR pp.fechaInicio <= :hasta)")
    List<Payslip> findClosedForReport(@Param("companyId") UUID companyId,
                                      @Param("desde") LocalDate desde,
                                      @Param("hasta") LocalDate hasta);
}
