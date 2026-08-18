package com.staffly.backend.security;

import com.staffly.backend.company.CompanyRepository;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.platform.EstadoPlatformAdmin;
import com.staffly.backend.platform.PlatformAdmin;
import com.staffly.backend.platform.PlatformAdminRepository;
import com.staffly.backend.security.dto.ChangePasswordRequest;
import com.staffly.backend.security.dto.LoginRequest;
import com.staffly.backend.security.dto.LoginResponse;
import com.staffly.backend.security.dto.RefreshResponse;
import com.staffly.backend.security.dto.UserSummary;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final CompanyRepository companyRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final long accessTokenMinutes;

    public AuthService(
            UserRepository userRepository,
            PlatformAdminRepository platformAdminRepository,
            CompanyRepository companyRepository,
            RevokedTokenRepository revokedTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            @Value("${staffly.security.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.companyRepository = companyRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email()).orElse(null);
        if (user != null) {
            return loginAsUser(user, request.password());
        }

        var platformAdmin = platformAdminRepository.findByEmail(request.email()).orElse(null);
        if (platformAdmin != null) {
            return loginAsPlatformAdmin(platformAdmin, request.password());
        }

        throw new InvalidCredentialsException();
    }

    private LoginResponse loginAsUser(User user, String password) {
        if (user.getEstado() != EstadoUsuario.ACTIVO || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!isCompanyActive(user.getCompanyId())) {
            throw new InvalidCredentialsException();
        }

        StafflyUserPrincipal principal = principalFromUser(user);
        UserSummary summary = new UserSummary(user.getId(), user.getEmail(), principal.getRol(), user.isDebeCambiarPassword());
        return buildLoginResponse(principal, summary);
    }

    private StafflyUserPrincipal principalFromUser(User user) {
        List<UUID> branchIds = user.getBranches().stream().map(b -> b.getId()).collect(Collectors.toList());
        return new StafflyUserPrincipal(user.getId(), user.getCompanyId(), Rol.valueOf(user.getRol().name()), branchIds);
    }

    private LoginResponse loginAsPlatformAdmin(PlatformAdmin platformAdmin, String password) {
        if (platformAdmin.getEstado() != EstadoPlatformAdmin.ACTIVO
                || !passwordEncoder.matches(password, platformAdmin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        StafflyUserPrincipal principal = new StafflyUserPrincipal(
                platformAdmin.getId(), null, Rol.SUPER_ADMIN, List.of());

        UserSummary summary = new UserSummary(platformAdmin.getId(), platformAdmin.getEmail(), Rol.SUPER_ADMIN, false);
        return buildLoginResponse(principal, summary);
    }

    private LoginResponse buildLoginResponse(StafflyUserPrincipal principal, UserSummary summary) {
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);
        return new LoginResponse(accessToken, refreshToken, accessTokenMinutes * 60, summary);
    }

    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        StafflyUserPrincipal claimedPrincipal = validateAndConsumeRefreshToken(refreshToken);
        StafflyUserPrincipal freshPrincipal = reloadPrincipal(claimedPrincipal);

        String newAccessToken = jwtService.generateAccessToken(freshPrincipal);
        String newRefreshToken = jwtService.generateRefreshToken(freshPrincipal);
        return new RefreshResponse(newAccessToken, newRefreshToken, accessTokenMinutes * 60);
    }

    /**
     * El refresh token es válido por días: los claims pueden describir una
     * cuenta que ya fue desactivada, una empresa suspendida, o un rol/sucursales
     * que ya cambiaron, después de emitido. Se revalida contra la DB en cada
     * refresh y se reconstruye el principal desde la fila actual — sin esto,
     * desactivar un usuario no le corta la sesión, y un cambio de rol o de
     * sucursales asignadas no tiene efecto hasta el próximo login manual.
     */
    private StafflyUserPrincipal reloadPrincipal(StafflyUserPrincipal principal) {
        if (principal.getRol() == Rol.SUPER_ADMIN) {
            PlatformAdmin platformAdmin = platformAdminRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new InvalidTokenException("La cuenta ya no existe"));
            if (platformAdmin.getEstado() != EstadoPlatformAdmin.ACTIVO) {
                throw new InvalidTokenException("La cuenta está desactivada");
            }
            return principal;
        }

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new InvalidTokenException("La cuenta ya no existe"));
        if (user.getEstado() != EstadoUsuario.ACTIVO) {
            throw new InvalidTokenException("La cuenta está desactivada");
        }
        if (!isCompanyActive(user.getCompanyId())) {
            throw new InvalidTokenException("La empresa está suspendida");
        }

        return principalFromUser(user);
    }

    private boolean isCompanyActive(UUID companyId) {
        return companyRepository.findById(companyId)
                .map(company -> company.getEstado() == EstadoEmpresa.ACTIVA)
                .orElse(false);
    }

    @Transactional
    public void logout(String refreshToken, UUID authenticatedUserId) {
        Claims claims = parseRefreshTokenOrThrow(refreshToken);
        if (!authenticatedUserId.toString().equals(claims.getSubject())) {
            throw new InvalidTokenException("El refresh token no pertenece al usuario autenticado");
        }
        revokeToken(claims);
    }

    @Transactional
    public void changePassword(StafflyUserPrincipal principal, ChangePasswordRequest request) {
        if (principal.getRol() == Rol.SUPER_ADMIN) {
            PlatformAdmin platformAdmin = platformAdminRepository.findById(principal.getUserId())
                    .orElseThrow(InvalidCredentialsException::new);
            if (!passwordEncoder.matches(request.currentPassword(), platformAdmin.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }
            platformAdmin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            platformAdminRepository.save(platformAdmin);
            return;
        }

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setDebeCambiarPassword(false);
        userRepository.save(user);
    }

    private StafflyUserPrincipal validateAndConsumeRefreshToken(String refreshToken) {
        Claims claims = parseRefreshTokenOrThrow(refreshToken);
        revokeToken(claims);
        return jwtService.toPrincipal(claims);
    }

    private Claims parseRefreshTokenOrThrow(String refreshToken) {
        Claims claims = jwtService.parseToken(refreshToken);
        if (!JwtService.TYPE_REFRESH.equals(jwtService.getTokenType(claims))) {
            throw new InvalidTokenException("El token no es un refresh token");
        }
        UUID jti = jwtService.getJti(claims);
        if (revokedTokenRepository.existsById(jti)) {
            throw new InvalidTokenException("El refresh token ya fue revocado");
        }
        return claims;
    }

    private void revokeToken(Claims claims) {
        // Purga oportunista: un jti expirado ya no puede usarse (el parseo
        // del JWT rechaza tokens vencidos antes de llegar acá), así que
        // conservarlo en la tabla no aporta nada — se limpia en cada
        // revocación para que la tabla no crezca indefinidamente.
        revokedTokenRepository.deleteByExpiraEnBefore(Instant.now());

        UUID jti = jwtService.getJti(claims);
        Instant expiraEn = claims.getExpiration().toInstant();
        revokedTokenRepository.save(new RevokedToken(jti, expiraEn));
    }
}
