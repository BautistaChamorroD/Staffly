package com.staffly.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final String CLAIM_COMPANY_ID = "company_id";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_BRANCH_IDS = "branch_ids";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_JTI = "jti";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(
            @Value("${staffly.security.jwt.secret}") String secret,
            @Value("${staffly.security.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${staffly.security.jwt.refresh-token-days}") long refreshTokenDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public String generateAccessToken(StafflyUserPrincipal principal) {
        Instant now = Instant.now();
        return buildToken(principal, TYPE_ACCESS, now, now.plus(accessTokenMinutes, ChronoUnit.MINUTES));
    }

    public String generateRefreshToken(StafflyUserPrincipal principal) {
        Instant now = Instant.now();
        return buildToken(principal, TYPE_REFRESH, now, now.plus(refreshTokenDays, ChronoUnit.DAYS));
    }

    private String buildToken(StafflyUserPrincipal principal, String type, Instant issuedAt, Instant expiresAt) {
        List<String> branchIds = principal.getBranchIds().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim(CLAIM_COMPANY_ID, principal.getCompanyId() != null ? principal.getCompanyId().toString() : null)
                .claim(CLAIM_ROLE, principal.getRol().name())
                .claim(CLAIM_BRANCH_IDS, branchIds)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida firma y expiración, y devuelve los claims. Lanza
     * io.jsonwebtoken.JwtException (o subclase) si el token es inválido,
     * expirado o fue alterado.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public StafflyUserPrincipal toPrincipal(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String companyIdClaim = claims.get(CLAIM_COMPANY_ID, String.class);
        UUID companyId = companyIdClaim != null ? UUID.fromString(companyIdClaim) : null;
        Rol rol = Rol.valueOf(claims.get(CLAIM_ROLE, String.class));
        List<UUID> branchIds = Optional.ofNullable(claims.get(CLAIM_BRANCH_IDS, List.class))
                .map(list -> (List<String>) list)
                .orElse(List.of())
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());

        return new StafflyUserPrincipal(userId, companyId, rol, branchIds);
    }

    public String getTokenType(Claims claims) {
        return claims.get(CLAIM_TYPE, String.class);
    }

    public UUID getJti(Claims claims) {
        return UUID.fromString(claims.get(CLAIM_JTI, String.class));
    }
}
