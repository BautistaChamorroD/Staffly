package com.staffly.backend.payslip;

import com.staffly.backend.common.BadRequestException;

/**
 * State pattern: centraliza las transiciones de estado válidas de un Payslip
 * y lanza BadRequestException si la transición no está permitida.
 *
 * <p>Transiciones válidas:
 * <ul>
 *   <li>GENERADO → PAGADO</li>
 *   <li>PAGADO   → ANULADO (solo Admin, validado en el controller)</li>
 * </ul>
 */
public final class PayslipStateTransition {

    private PayslipStateTransition() {}

    public static void validate(EstadoRecibo current, EstadoRecibo next) {
        boolean valid = switch (current) {
            case GENERADO -> next == EstadoRecibo.PAGADO;
            case PAGADO   -> next == EstadoRecibo.ANULADO;
            case ANULADO  -> false;
        };

        if (!valid) {
            throw new BadRequestException(
                    "Transición de estado inválida: " + current + " → " + next);
        }
    }
}
