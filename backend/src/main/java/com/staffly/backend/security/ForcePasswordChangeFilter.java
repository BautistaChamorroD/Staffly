package com.staffly.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.common.ApiError;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Bloquea cualquier request autenticado de un usuario con contraseña
 * provisoria sin cambiar (RF-01), salvo los dos endpoints que necesita para
 * salir de ese estado. Corre después de JwtAuthenticationFilter (necesita el
 * principal ya resuelto en el SecurityContext) y revalida contra la DB en
 * cada request — no contra un claim del JWT — para que el cambio de
 * contraseña tenga efecto inmediato con el mismo access token, sin esperar
 * a un refresh.
 */
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout");

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ForcePasswordChangeFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!ALLOWED_PATHS.contains(request.getRequestURI())) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof StafflyUserPrincipal principal
                    && principal.getRol() != Rol.SUPER_ADMIN
                    && userRepository.findById(principal.getUserId())
                            .map(User::isDebeCambiarPassword)
                            .orElse(false)) {

                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        ApiError.of("PASSWORD_CHANGE_REQUIRED", "Debe cambiar su contraseña antes de continuar"));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
