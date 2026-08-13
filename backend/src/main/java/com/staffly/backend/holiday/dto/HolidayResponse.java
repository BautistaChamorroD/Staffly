// backend/src/main/java/com/staffly/backend/holiday/dto/HolidayResponse.java
package com.staffly.backend.holiday.dto;

import com.staffly.backend.holiday.Holiday;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(UUID id, UUID branchId, LocalDate fecha, String nombre, boolean recurrente) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getBranchId(),
                holiday.getFecha(),
                holiday.getNombre(),
                holiday.isRecurrente());
    }
}
