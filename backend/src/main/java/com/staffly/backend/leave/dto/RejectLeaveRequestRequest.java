package com.staffly.backend.leave.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectLeaveRequestRequest(@NotBlank String motivo) {
}
