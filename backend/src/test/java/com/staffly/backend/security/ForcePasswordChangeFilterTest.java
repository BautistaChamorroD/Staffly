package com.staffly.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.staffly.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de unidad del filtro en aislamiento (sin levantar el contexto de
 * Spring) — issue #168, punto 3: antes de este test, la exclusión de
 * SUPER_ADMIN era "seguridad accidental" sin cobertura: si alguien borraba
 * esa línea, un id de PlatformAdmin no resuelve en UserRepository →
 * `.orElse(...)` decidía el resultado sin que ningún test lo notara.
 */
@ExtendWith(MockitoExtension.class)
class ForcePasswordChangeFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminNeverBlockedEvenIfLookupFindsNothing() throws Exception {
        UUID platformAdminId = UUID.randomUUID();
        authenticateAs(platformAdminId, Rol.SUPER_ADMIN, null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ForcePasswordChangeFilter(userRepository, objectMapper)
                .doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // corta por rol ANTES de tocar el repositorio — la exclusión de
        // SUPER_ADMIN no depende de que el lookup resuelva.
        verifyNoInteractions(userRepository);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void failSafeBlocksWhenLookupFindsNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, Rol.ADMIN, UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ForcePasswordChangeFilter(userRepository, objectMapper)
                .doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void failSafeBlocksWhenRepositoryThrows() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, Rol.ADMIN, UUID.randomUUID());
        when(userRepository.findById(userId)).thenThrow(new RuntimeException("connection lost"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ForcePasswordChangeFilter(userRepository, objectMapper)
                .doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void allowedPathMatchesEvenWithContextPathSet() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId, Rol.ADMIN, UUID.randomUUID());

        // simula server.servlet.context-path=/api-gateway configurado a
        // futuro (issue #168, punto 2) — getRequestURI() incluiría ese
        // prefijo ("/api-gateway/api/v1/auth/change-password"),
        // getServletPath() no.
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api-gateway/api/v1/auth/change-password");
        request.setContextPath("/api-gateway");
        request.setServletPath("/api/v1/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ForcePasswordChangeFilter(userRepository, objectMapper)
                .doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository);
    }

    private void authenticateAs(UUID userId, Rol rol, UUID companyId) {
        StafflyUserPrincipal principal = new StafflyUserPrincipal(userId, companyId, rol, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
