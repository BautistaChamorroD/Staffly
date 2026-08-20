package com.staffly.backend.branch;

import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BranchServiceTest {

    @Autowired
    private BranchService branchService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void employeeRoleCannotAccessBranchRegardlessOfController() {
        UUID companyId = createCompany();
        Branch branch = createBranch(companyId);

        // issue #164 (seguimiento de AUD-03): sin defensa propia, isInScope()
        // trataba a cualquier rol distinto de SUPERVISOR (incluido EMPLOYEE)
        // como "ve cualquier sucursal" — probado a nivel de servicio, sin
        // depender de que el @PreAuthorize del controller siga bloqueando
        // este rol para siempre.
        StafflyUserPrincipal employeePrincipal =
                new StafflyUserPrincipal(UUID.randomUUID(), companyId, Rol.EMPLOYEE, List.of());

        assertThatThrownBy(() -> branchService.getById(branch.getId(), employeePrincipal))
                .isInstanceOf(com.staffly.backend.common.ResourceNotFoundException.class);
    }

    private UUID createCompany() {
        Company c = new Company();
        c.setNombre("Empresa Test");
        c.setRazonSocial("Empresa Test SRL");
        c.setPais("AR");
        c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(c);
        entityManager.flush();
        return c.getId();
    }

    private Branch createBranch(UUID companyId) {
        Branch b = new Branch();
        b.setCompanyId(companyId);
        b.setNombre("Sucursal Test");
        b.setDireccion("Dir");
        b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(b);
        entityManager.flush();
        return b;
    }
}
