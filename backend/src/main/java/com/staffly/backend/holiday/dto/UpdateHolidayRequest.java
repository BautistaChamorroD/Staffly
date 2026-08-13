// backend/src/main/java/com/staffly/backend/holiday/dto/UpdateHolidayRequest.java
package com.staffly.backend.holiday.dto;

import java.time.LocalDate;
import java.util.UUID;

// Actualización parcial: null = no cambiar.
// recurrente es Boolean boxeado para distinguir null (no cambiar) de false (poner en false).
// branchId: null = no cambiar. No se puede convertir un feriado de branch-específico
// a global vía PATCH — delete + recreate en ese caso.
public record UpdateHolidayRequest(UUID branchId, LocalDate fecha, String nombre, Boolean recurrente) {
}
