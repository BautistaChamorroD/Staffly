package com.staffly.backend.employee.dto;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record EmployeeResponse(
        UUID id,
        String nombre,
        String apellido,
        String documento,
        LocalDate fechaNacimiento,
        LocalDate fechaIngreso,
        LocalDate fechaEgreso,
        TipoContrato tipoContrato,
        String categoria,
        BigDecimal sueldoBase,
        String telefono,
        String emailContacto,
        EstadoLaboral estadoLaboral,
        EstadoLiquidacion estadoLiquidacion,
        List<UUID> branchIds) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getNombre(),
                employee.getApellido(),
                employee.getDocumento(),
                employee.getFechaNacimiento(),
                employee.getFechaIngreso(),
                employee.getFechaEgreso(),
                employee.getTipoContrato(),
                employee.getCategoria(),
                employee.getSueldoBase(),
                employee.getTelefono(),
                employee.getEmailContacto(),
                employee.getEstadoLaboral(),
                employee.getEstadoLiquidacion(),
                employee.getBranches().stream().map(Branch::getId).collect(Collectors.toList()));
    }
}
