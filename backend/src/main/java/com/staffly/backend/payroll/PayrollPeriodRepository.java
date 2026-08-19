package com.staffly.backend.payroll;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    List<PayrollPeriod> findByCompanyIdOrderByFechaInicioDesc(UUID companyId);

    List<PayrollPeriod> findByCompanyIdAndEstadoOrderByFechaInicioDesc(UUID companyId, EstadoPeriodo estado);

    Optional<PayrollPeriod> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Igual a {@link #findByIdAndCompanyId} pero toma un lock exclusivo (SELECT ...
     * FOR UPDATE) — usado al cerrar un período para serializar dos requests de
     * cierre casi simultáneas contra el mismo período (issue #147, parte a). El
     * segundo request queda bloqueado hasta que el primero commitea, y al
     * re-leer encuentra el período ya CERRADO.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayrollPeriod p WHERE p.id = :id AND p.companyId = :companyId")
    Optional<PayrollPeriod> findByIdAndCompanyIdForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndEstadoIn(UUID companyId, List<EstadoPeriodo> estados);

    boolean existsByCompanyIdAndFechaInicioAfterAndEstado(UUID companyId, LocalDate fechaInicio, EstadoPeriodo estado);
}
