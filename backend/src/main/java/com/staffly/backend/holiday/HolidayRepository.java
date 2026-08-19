package com.staffly.backend.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    /**
     * Lookup por id + company_id: el tenantFilter de Hibernate no cubre
     * findById/EntityManager.find(), por eso se usa este método para cualquier
     * lookup de un Holiday específico.
     */
    Optional<Holiday> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Todos los feriados de la empresa (sin filtro de año). */
    List<Holiday> findByCompanyId(UUID companyId);

    /** Feriados de la empresa dentro de un rango de fechas (para ?anio=). */
    List<Holiday> findByCompanyIdAndFechaBetween(UUID companyId, LocalDate desde, LocalDate hasta);

    /** Candidatos para {@link #findApplicableFechasInRange}: exactos en rango, o cualquier recurrente. */
    @Query("SELECT h FROM Holiday h WHERE h.companyId = :companyId " +
           "AND (h.fecha BETWEEN :desde AND :hasta OR h.recurrente = true)")
    List<Holiday> findCandidatesForRange(
            @Param("companyId") UUID companyId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /**
     * Fechas de feriado aplicables a un rango, para cálculo de nómina: los no recurrentes
     * solo si caen literalmente en el rango, y los recurrentes en la ocurrencia de su
     * (mes, día) para cada año que el rango toca — sin importar el año en que fueron
     * cargados originalmente (ver AUD-11 / issue #138).
     */
    default List<LocalDate> findApplicableFechasInRange(UUID companyId, LocalDate desde, LocalDate hasta) {
        List<LocalDate> aplicables = new ArrayList<>();
        for (Holiday h : findCandidatesForRange(companyId, desde, hasta)) {
            if (!h.isRecurrente()) {
                if (!h.getFecha().isBefore(desde) && !h.getFecha().isAfter(hasta)) {
                    aplicables.add(h.getFecha());
                }
                continue;
            }
            for (int anio = desde.getYear(); anio <= hasta.getYear(); anio++) {
                try {
                    LocalDate ocurrencia = h.getFecha().withYear(anio);
                    if (!ocurrencia.isBefore(desde) && !ocurrencia.isAfter(hasta)) {
                        aplicables.add(ocurrencia);
                    }
                } catch (DateTimeException e) {
                    // 29 de febrero en un año no bisiesto: no hay ocurrencia ese año.
                }
            }
        }
        return aplicables;
    }

    // ── métodos de deduplicación ────────────────────────────────────────────────
    // Se usan @Query explícitas porque Spring Data JPA / Hibernate 6.x no puede
    // resolver "BranchId" como path derivado a través de una asociación @ManyToOne.
    // Los métodos del spec conservan sus nombres exactos para que el servicio
    // los invoque sin cambios en Task 2.

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Holiday h WHERE h.companyId = :companyId AND h.branch IS NULL AND h.fecha = :fecha")
    boolean existsByCompanyIdAndBranchIdIsNullAndFecha(
            @Param("companyId") UUID companyId, @Param("fecha") LocalDate fecha);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Holiday h WHERE h.companyId = :companyId AND h.branch.id = :branchId AND h.fecha = :fecha")
    boolean existsByCompanyIdAndBranchIdAndFecha(
            @Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
            @Param("fecha") LocalDate fecha);

    /** Self-exclusion en PATCH: excluye al propio feriado de la búsqueda de duplicados. */
    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Holiday h WHERE h.companyId = :companyId AND h.branch IS NULL " +
           "AND h.fecha = :fecha AND h.id <> :excludeId")
    boolean existsByCompanyIdAndBranchIdIsNullAndFechaAndIdNot(
            @Param("companyId") UUID companyId, @Param("fecha") LocalDate fecha,
            @Param("excludeId") UUID excludeId);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END " +
           "FROM Holiday h WHERE h.companyId = :companyId AND h.branch.id = :branchId " +
           "AND h.fecha = :fecha AND h.id <> :excludeId")
    boolean existsByCompanyIdAndBranchIdAndFechaAndIdNot(
            @Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
            @Param("fecha") LocalDate fecha, @Param("excludeId") UUID excludeId);
}
