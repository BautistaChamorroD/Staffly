package com.staffly.backend.user.dto;

import com.staffly.backend.user.RolUsuario;

import java.util.List;
import java.util.UUID;

/**
 * Actualización parcial: campos nulos se dejan sin tocar. El estado se
 * cambia únicamente vía PATCH /users/{id}/status, no acá.
 */
public record UpdateUserRequest(RolUsuario rol, List<UUID> branchIds) {
}
