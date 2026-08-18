package com.staffly.backend.employee;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void employeeRoleNeverMatchesAnyEmployeeRegardlessOfController() {
        UUID companyId = createCompany();
        Branch branch = createBranch(companyId);
        Employee otherEmployee = createEmployee(companyId, branch);

        // Principal EMPLOYEE construido directamente (sin pasar por login/HTTP),
        // para probar que EmployeeService se defiende solo, sin depender de que
        // el @PreAuthorize del controller siga bloqueando este rol para siempre.
        StafflyUserPrincipal employeePrincipal =
                new StafflyUserPrincipal(UUID.randomUUID(), companyId, Rol.EMPLOYEE, List.of());

        assertThatThrownBy(() -> employeeService.getById(otherEmployee.getId(), employeePrincipal))
                .isInstanceOf(AccessDeniedException.class);
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

    private Employee createEmployee(UUID companyId, Branch branch) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre("Ana");
        e.setApellido("García");
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(100000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e);
        entityManager.flush();
        return e;
    }
}
