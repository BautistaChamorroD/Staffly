package com.staffly.backend.user.dto;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.RolUsuario;
import com.staffly.backend.user.User;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String email,
        RolUsuario rol,
        EstadoUsuario estado,
        boolean debeCambiarPassword,
        UUID employeeId,
        List<UUID> branchIds) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRol(),
                user.getEstado(),
                user.isDebeCambiarPassword(),
                user.getEmployee() != null ? user.getEmployee().getId() : null,
                user.getBranches().stream().map(Branch::getId).collect(Collectors.toList()));
    }
}
