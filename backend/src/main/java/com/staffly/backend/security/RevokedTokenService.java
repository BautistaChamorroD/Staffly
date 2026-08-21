package com.staffly.backend.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoca un refresh token en una transacción INDEPENDIENTE de la que lo
 * invoca (issue #166, seguimiento de AUD-04). {@code AuthService.refresh()}
 * necesita que el token quede en la lista negra pase lo que pase después —
 * si {@code reloadPrincipal} rechaza el refresh (cuenta desactivada, empresa
 * suspendida), el rollback de esa transacción no debe deshacer también la
 * revocación, o el token queda reutilizable indefinidamente hasta que
 * expire solo.
 */
@Component
public class RevokedTokenService {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(UUID jti, Instant expiraEn) {
        // Purga oportunista: un jti expirado ya no puede usarse (el parseo
        // del JWT rechaza tokens vencidos antes de llegar acá), así que
        // conservarlo en la tabla no aporta nada — se limpia en cada
        // revocación para que la tabla no crezca indefinidamente.
        revokedTokenRepository.deleteByExpiraEnBefore(Instant.now());
        revokedTokenRepository.save(new RevokedToken(jti, expiraEn));
    }
}
