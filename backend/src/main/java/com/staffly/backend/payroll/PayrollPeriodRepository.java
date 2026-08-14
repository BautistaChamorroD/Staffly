package com.staffly.backend.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    List<PayrollPeriod> findByCompanyIdOrderByFechaInicioDesc(UUID companyId);

    List<PayrollPeriod> findByCompanyIdAndEstadoOrderByFechaInicioDesc(UUID companyId, EstadoPeriodo estado);

    Optional<PayrollPeriod> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndEstadoIn(UUID companyId, List<EstadoPeriodo> estados);

    boolean existsByCompanyIdAndFechaInicioAfterAndEstado(UUID companyId, LocalDate fechaInicio, EstadoPeriodo estado);
}
