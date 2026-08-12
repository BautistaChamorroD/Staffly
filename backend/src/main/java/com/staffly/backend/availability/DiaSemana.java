package com.staffly.backend.availability;

/**
 * Día de la semana de una franja de disponibilidad recurrente. El orden de
 * declaración (LUNES primero) es el orden semanal que usa el service para
 * ordenar el listado — se persiste como STRING, así que un ORDER BY en SQL
 * sería alfabético y no sirve.
 */
public enum DiaSemana {
    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO
}
