package com.staffly.backend.payslip.dto;

import jakarta.validation.constraints.Size;

public record VoidPayslipRequest(@Size(max = 1000) String motivoAnulacion) {}
