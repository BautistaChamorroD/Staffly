package com.staffly.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-characters-1234567890";

    private JwtService newService(long accessMinutes, long refreshDays) {
        return new JwtService(SECRET, accessMinutes, refreshDays);
    }

    @Test
    void generatesAndParsesAccessTokenWithExpectedClaims() {
        JwtService jwtService = newService(30, 7);
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        StafflyUserPrincipal principal = new StafflyUserPrincipal(userId, companyId, Rol.ADMIN, List.of(branchId));

        String token = jwtService.generateAccessToken(principal);
        Claims claims = jwtService.parseToken(token);

        assertThat(jwtService.getTokenType(claims)).isEqualTo(JwtService.TYPE_ACCESS);
        StafflyUserPrincipal parsed = jwtService.toPrincipal(claims);
        assertThat(parsed.getUserId()).isEqualTo(userId);
        assertThat(parsed.getCompanyId()).isEqualTo(companyId);
        assertThat(parsed.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(parsed.getBranchIds()).containsExactly(branchId);
    }

    @Test
    void generatesRefreshTokenWithNullCompanyIdForSuperAdmin() {
        JwtService jwtService = newService(30, 7);
        UUID userId = UUID.randomUUID();
        StafflyUserPrincipal principal = new StafflyUserPrincipal(userId, null, Rol.SUPER_ADMIN, List.of());

        String token = jwtService.generateRefreshToken(principal);
        Claims claims = jwtService.parseToken(token);

        assertThat(jwtService.getTokenType(claims)).isEqualTo(JwtService.TYPE_REFRESH);
        StafflyUserPrincipal parsed = jwtService.toPrincipal(claims);
        assertThat(parsed.getCompanyId()).isNull();
        assertThat(parsed.getRol()).isEqualTo(Rol.SUPER_ADMIN);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwtService = newService(0, 7);
        StafflyUserPrincipal principal = new StafflyUserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Rol.EMPLOYEE, List.of());

        String token = jwtService.generateAccessToken(principal);

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = newService(30, 7);
        JwtService verifier = new JwtService(
                "a-completely-different-secret-key-1234567890-abcdefgh", 30, 7);
        StafflyUserPrincipal principal = new StafflyUserPrincipal(UUID.randomUUID(), UUID.randomUUID(), Rol.RRHH, List.of());

        String token = issuer.generateAccessToken(principal);

        assertThatThrownBy(() -> verifier.parseToken(token)).isInstanceOf(SignatureException.class);
    }
}
