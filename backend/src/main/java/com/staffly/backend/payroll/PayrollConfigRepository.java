package com.staffly.backend.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayrollConfigRepository extends JpaRepository<PayrollConfig, UUID> {

    Optional<PayrollConfig> findByCompanyId(UUID companyId);
}
