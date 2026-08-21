package com.staffly.backend.payslip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidPayslipRequest(
        @NotBlank(message = "El motivo de anulacion es obligatorio")
        @Size(max = 1000, message = "El motivo de anulacion no puede superar 1000 caracteres")
        String motivoAnulacion
) {}
