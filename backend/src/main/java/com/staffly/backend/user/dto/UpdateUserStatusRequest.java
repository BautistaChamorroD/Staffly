package com.staffly.backend.user.dto;

import com.staffly.backend.user.EstadoUsuario;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull EstadoUsuario estado) {
}
