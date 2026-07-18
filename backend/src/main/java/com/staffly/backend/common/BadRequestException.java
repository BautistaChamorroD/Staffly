package com.staffly.backend.common;

/**
 * Regla de dominio que la validación de beans no puede expresar (ej.
 * comparación entre un campo del request y el estado guardado de la
 * entidad). Se mapea a 400 VALIDATION_ERROR en GlobalExceptionHandler.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
