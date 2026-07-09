package com.staffly.backend.security;

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
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final long accessTokenMinutes;

    public AuthService(
            UserRepository userRepository,
            PlatformAdminRepository platformAdminRepository,
            RevokedTokenRepository revokedTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            @Value("${staffly.security.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.userRepository = userRepository;
        this.platformAdminRepository = platformAdminRepository;
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

        List<UUID> branchIds = user.getBranches().stream().map(b -> b.getId()).collect(Collectors.toList());
        StafflyUserPrincipal principal = new StafflyUserPrincipal(
                user.getId(), user.getCompanyId(), Rol.valueOf(user.getRol().name()), branchIds);

        UserSummary summary = new UserSummary(user.getId(), user.getEmail(), principal.getRol(), user.isDebeCambiarPassword());
        return buildLoginResponse(principal, summary);
    }

    private LoginResponse loginAsPlatformAdmin(PlatformAdmin platformAdmin, String password) {
        if (!passwordEncoder.matches(password, platformAdmin.getPasswordHash())) {
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
        StafflyUserPrincipal principal = validateAndConsumeRefreshToken(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(principal);
        String newRefreshToken = jwtService.generateRefreshToken(principal);
        return new RefreshResponse(newAccessToken, newRefreshToken, accessTokenMinutes * 60);
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
        UUID jti = jwtService.getJti(claims);
        Instant expiraEn = claims.getExpiration().toInstant();
        revokedTokenRepository.save(new RevokedToken(jti, expiraEn));
    }
}
