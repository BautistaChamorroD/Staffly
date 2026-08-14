package com.staffly.backend.payroll.decorator;

import com.staffly.backend.payroll.TipoConcepto;

import java.math.BigDecimal;

/** Línea de detalle de una deducción — aparece en el desglose del recibo. */
public record DeduccionLinea(String nombre, TipoConcepto tipo, BigDecimal monto) {}
