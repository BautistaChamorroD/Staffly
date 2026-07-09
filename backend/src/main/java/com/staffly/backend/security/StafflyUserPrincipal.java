package com.staffly.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Principal autenticado construido directamente a partir de los claims de
 * un JWT ya validado (sin lookup a base de datos). companyId es null para
 * SUPER_ADMIN (PlatformAdmin no pertenece a ninguna empresa).
 */
public class StafflyUserPrincipal {

    private final UUID userId;
    private final UUID companyId;
    private final Rol rol;
    private final List<UUID> branchIds;

    public StafflyUserPrincipal(UUID userId, UUID companyId, Rol rol, List<UUID> branchIds) {
        this.userId = userId;
        this.companyId = companyId;
        this.rol = rol;
        this.branchIds = branchIds;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Rol getRol() {
        return rol;
    }

    public List<UUID> getBranchIds() {
        return branchIds;
    }

    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }
}
