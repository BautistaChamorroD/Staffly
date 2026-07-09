package com.staffly.backend.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Operador de la plataforma (rol SUPER_ADMIN): da de alta empresas nuevas.
 * No pertenece a ninguna Company y por eso NO extiende TenantAwareEntity
 * (a diferencia de todo el resto de las entidades de negocio, salvo Company).
 */
@Entity
@Table(name = "platform_admin")
public class PlatformAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPlatformAdmin estado = EstadoPlatformAdmin.ACTIVO;

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public EstadoPlatformAdmin getEstado() {
        return estado;
    }

    public void setEstado(EstadoPlatformAdmin estado) {
        this.estado = estado;
    }
}
