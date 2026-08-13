package com.staffly.backend.common;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ScheduleOverlapBatchException extends RuntimeException {

    public record ConflictDetail(LocalDate fecha, UUID turnoExistenteId) {}

    private final List<ConflictDetail> conflictos;

    public ScheduleOverlapBatchException(List<ConflictDetail> conflictos) {
        super("El empleado ya tiene turnos en las fechas indicadas");
        this.conflictos = List.copyOf(conflictos);
    }

    public List<ConflictDetail> getConflictos() {
        return conflictos;
    }
}
