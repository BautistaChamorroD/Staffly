// backend/src/main/java/com/staffly/backend/holiday/dto/CreateHolidayRequest.java
package com.staffly.backend.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateHolidayRequest(
        UUID branchId,
        @NotNull LocalDate fecha,
        @NotBlank String nombre,
        boolean recurrente) {
}
