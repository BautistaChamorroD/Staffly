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
import java.util.Objects;
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
            "/api/v1/auth/logout",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh");

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

        // getServletPath()+getPathInfo() son relativos al contexto de la
        // aplicación; getRequestURI() incluye el context path completo. Hoy
        // no hay `server.servlet.context-path` configurado en ningún perfil,
        // así que ambos coinciden — pero si alguna vez se agrega uno,
        // comparar contra getRequestURI() dejaría de matchear ALLOWED_PATHS
        // silenciosamente, bloqueando incluso /auth/change-password (issue
        // #168, punto 2).
        String path = request.getServletPath() + Objects.toString(request.getPathInfo(), "");

        if (!ALLOWED_PATHS.contains(path)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof StafflyUserPrincipal principal
                    && principal.getRol() != Rol.SUPER_ADMIN
                    && requiresPasswordChange(principal)) {

                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(),
                        ApiError.of("PASSWORD_CHANGE_REQUIRED", "Debe cambiar su contraseña antes de continuar"));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Fail-safe: si el lookup no resuelve o explota, exige el cambio de
     * contraseña en vez de dejar pasar (issue #168, puntos 1 y 4) — un
     * filtro de seguridad nunca debería fallar abierto. El caso "no
     * resuelve" es hoy inalcanzable (los usuarios se desactivan, nunca se
     * borran) pero SUPER_ADMIN ya cortó antes de llegar acá, así que este
     * default nunca los bloquea a ellos.
     */
    private boolean requiresPasswordChange(StafflyUserPrincipal principal) {
        try {
            return userRepository.findById(principal.getUserId())
                    .map(User::isDebeCambiarPassword)
                    .orElse(true);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
