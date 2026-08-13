package com.staffly.backend.common;

import java.util.List;
import java.util.UUID;

public class LeaveApprovalConflictException extends RuntimeException {

    private final List<UUID> turnosConflictivos;

    public LeaveApprovalConflictException(List<UUID> turnosConflictivos) {
        super("La licencia se superpone con turnos existentes del empleado");
        this.turnosConflictivos = List.copyOf(turnosConflictivos);
    }

    public List<UUID> getTurnosConflictivos() {
        return turnosConflictivos;
    }
}
