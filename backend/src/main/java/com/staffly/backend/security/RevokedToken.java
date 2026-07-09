package com.staffly.backend.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Bookkeeping técnico de seguridad: un refresh token revocado (por logout o
 * por rotation en /auth/refresh) antes de su expiración natural. No extiende
 * TenantAwareEntity — se busca únicamente por jti, nunca por empresa.
 */
@Entity
@Table(name = "revoked_token")
public class RevokedToken {

    @Id
    @Column(name = "jti", updatable = false, nullable = false)
    private UUID jti;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    protected RevokedToken() {
    }

    public RevokedToken(UUID jti, Instant expiraEn) {
        this.jti = jti;
        this.expiraEn = expiraEn;
    }

    public UUID getJti() {
        return jti;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }
}
