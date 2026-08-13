package com.staffly.backend.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByIdAndCompanyId(UUID id, UUID companyId);

    List<LeaveType> findByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndNombre(UUID companyId, String nombre);

    boolean existsByCompanyIdAndNombreAndIdNot(UUID companyId, String nombre, UUID excludeId);
}
