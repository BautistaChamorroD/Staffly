package com.staffly.backend.leave;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.leave.dto.LeaveRequestResponse;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.RolUsuario;
import com.staffly.backend.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LeaveRequestServiceTest {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void employeeRoleOnlySeesOwnRequestsRegardlessOfController() {
        UUID companyId = createCompany();
        Branch branch = createBranch(companyId);
        LeaveType leaveType = createLeaveType(companyId);

        Employee self = createEmployee(companyId, branch, "Juan", "Pérez");
        Employee other = createEmployee(companyId, branch, "Ana", "García");
        UUID selfUserId = createUser(companyId, "juan@a.com", self);

        createLeaveRequest(companyId, self, leaveType);
        createLeaveRequest(companyId, other, leaveType);

        // issue #164 (seguimiento de AUD-03): confirma a nivel de servicio,
        // sin pasar por HTTP, que un EMPLOYEE nunca ve solicitudes de otro
        // empleado — list() ya scopea la query por su propio employeeId
        // antes de aplicar supervisorFilter(), a diferencia del gap real que
        // sí tenían EmployeeService.list() y BranchService.isInScope().
        StafflyUserPrincipal employeePrincipal =
                new StafflyUserPrincipal(selfUserId, companyId, Rol.EMPLOYEE, List.of());

        List<LeaveRequestResponse> result = leaveRequestService.list(null, null, employeePrincipal);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeeId()).isEqualTo(self.getId());
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

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre(nombre);
        e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(100_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e);
        entityManager.flush();
        return e;
    }

    private LeaveType createLeaveType(UUID companyId) {
        LeaveType lt = new LeaveType();
        lt.setCompanyId(companyId);
        lt.setNombre("Vacaciones");
        lt.setEsPaga(true);
        entityManager.persist(lt);
        entityManager.flush();
        return lt;
    }

    private UUID createUser(UUID companyId, String email, Employee employee) {
        User u = new User();
        u.setCompanyId(companyId);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRol(RolUsuario.EMPLOYEE);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setDebeCambiarPassword(false);
        u.setEmployee(employee);
        entityManager.persist(u);
        entityManager.flush();
        return u.getId();
    }

    private LeaveRequest createLeaveRequest(UUID companyId, Employee employee, LeaveType leaveType) {
        LeaveRequest lr = new LeaveRequest();
        lr.setCompanyId(companyId);
        lr.setEmployee(employee);
        lr.setLeaveType(leaveType);
        lr.setFechaInicio(LocalDate.of(2026, 9, 1));
        lr.setFechaFin(LocalDate.of(2026, 9, 5));
        lr.setEstado(EstadoLicencia.PENDIENTE);
        entityManager.persist(lr);
        entityManager.flush();
        return lr;
    }
}
