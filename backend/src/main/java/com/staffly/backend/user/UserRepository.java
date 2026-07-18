package com.staffly.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Chequeo de unicidad de email SIN el filtro de tenant: query nativa a
     * propósito (los @Filter de Hibernate solo aplican a HQL/Criteria). El
     * email es único global — el login no discrimina por empresa — así que
     * un duplicado en OTRA empresa también debe detectarse; la derived query
     * findByEmail corre con el filtro activo y no lo vería. Justificado
     * según la regla de backend/CLAUDE.md sobre bypass del tenantFilter.
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM app_user WHERE email = :email", nativeQuery = true)
    boolean emailExistsAcrossAllCompanies(@Param("email") String email);

    /**
     * Listado con filtros opcionales a nivel query (nulos = sin filtro).
     * El tenantFilter de Hibernate acota por empresa por encima de esto.
     */
    @Query("SELECT u FROM User u WHERE (:rol IS NULL OR u.rol = :rol) AND (:estado IS NULL OR u.estado = :estado)")
    List<User> findAllFiltered(@Param("rol") RolUsuario rol, @Param("estado") EstadoUsuario estado);
}
