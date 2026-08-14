package com.staffly.backend.payslip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
