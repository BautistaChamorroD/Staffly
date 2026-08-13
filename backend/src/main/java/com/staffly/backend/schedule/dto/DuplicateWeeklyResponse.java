package com.staffly.backend.schedule.dto;

import java.util.List;

public record DuplicateWeeklyResponse(
        List<ScheduleResponse> turnosCreados,
        String advertencia
) {}
