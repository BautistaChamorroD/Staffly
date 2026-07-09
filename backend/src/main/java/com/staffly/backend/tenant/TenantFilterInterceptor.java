package com.staffly.backend.tenant;

import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Activa el filtro "tenantFilter" (definido en TenantAwareEntity) con el
 * company_id del usuario autenticado. No hace nada si no hay principal
 * autenticado, o si es SUPER_ADMIN (companyId nulo: no pertenece a ninguna
 * empresa, no necesita el filtro).
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;

    public TenantFilterInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof StafflyUserPrincipal principal
                && principal.getCompanyId() != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter("tenantFilter")
                    .setParameter("companyId", principal.getCompanyId());
        }

        return true;
    }
}
