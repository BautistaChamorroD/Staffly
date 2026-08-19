package com.staffly.backend.payroll;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.CompanyRepository;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.PayslipRepository;
import com.staffly.backend.schedule.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Issue #149 (AUD-28): confirma empíricamente que close() es atómico de punta a
 * punta. Sin @Transactional a nivel de test a propósito — necesitamos que la
 * única transacción en juego sea la del propio close(), para poder verificar
 * después (con una lectura fresca) si algo quedó persistido a mitad de camino.
 */
@SpringBootTest
class PayrollPeriodCloseAtomicityTest {

    @Autowired private PayrollPeriodCloseService closeService;
    @Autowired private CompanyRepository       companyRepository;
    @Autowired private BranchRepository        branchRepository;
    @Autowired private EmployeeRepository      employeeRepository;
    @Autowired private PayrollConfigRepository configRepository;
    @Autowired private PayrollPeriodRepository periodRepository;
    @Autowired private PayslipRepository       payslipRepository;

    @MockitoSpyBean private ScheduleRepository scheduleRepository;

    @Test
    void excepcionAMitadDelLoop_noDejaPayslipsNiPeriodoParcialmenteCerrado() {
        UUID companyId = createCompany();
        Branch branch = createBranch(companyId);
        Employee emp1 = createEmployee(companyId, branch, "Juan", "Pérez");
        Employee emp2 = createEmployee(companyId, branch, "Ana", "García");
        createPayrollConfig(companyId);
        PayrollPeriod period = createPeriod(companyId);

        // emp1 se procesa normal (sin turnos, bruto 0); emp2 dispara una excepción
        // a mitad del cierre — no depende del orden real en que el loop los visite.
        doAnswer(invocation -> {
            UUID employeeId = invocation.getArgument(1);
            if (employeeId.equals(emp2.getId())) {
                throw new RuntimeException("boom — falla simulada a mitad del cierre");
            }
            return List.of();
        }).when(scheduleRepository).findByCompanyIdAndEmployeeIdInRange(any(), any(), any(), any());

        assertThatThrownBy(() -> closeService.close(period.getId(), companyId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");

        List<Payslip> payslips = payslipRepository.findByCompanyId(companyId);
        assertThat(payslips).as("no debe quedar ningún Payslip persistido tras el rollback").isEmpty();

        PayrollPeriod refreshed = periodRepository.findByIdAndCompanyId(period.getId(), companyId).orElseThrow();
        assertThat(refreshed.getEstado()).isEqualTo(EstadoPeriodo.ABIERTO);
        assertThat(refreshed.getFechaCierre()).isNull();

        // ninguno de los dos debe quedar AL_DIA — ni siquiera el que alcanzó a
        // procesarse antes de la excepción (el orden real del loop no está
        // garantizado, por eso se verifican ambos en vez de asumir cuál fue primero).
        for (Employee emp : List.of(emp1, emp2)) {
            Employee refreshedEmp = employeeRepository.findByIdAndCompanyId(emp.getId(), companyId).orElseThrow();
            assertThat(refreshedEmp.getEstadoLiquidacion()).isEqualTo(EstadoLiquidacion.PENDIENTE);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany() {
        Company c = new Company();
        c.setNombre("Empresa Atomicidad");
        c.setRazonSocial("Empresa Atomicidad SRL");
        c.setPais("AR");
        c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        return companyRepository.save(c).getId();
    }

    private Branch createBranch(UUID companyId) {
        Branch b = new Branch();
        b.setCompanyId(companyId);
        b.setNombre("Sucursal");
        b.setDireccion("Dir");
        b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        return branchRepository.save(b);
    }

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre(nombre);
        e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(200_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.PENDIENTE);
        e.getBranches().add(branch);
        return employeeRepository.save(e);
    }

    private void createPayrollConfig(UUID companyId) {
        PayrollConfig cfg = new PayrollConfig();
        cfg.setCompanyId(companyId);
        cfg.setUmbralHorasExtra(BigDecimal.valueOf(8));
        cfg.setTipoUmbral(TipoUmbral.DIARIO);
        cfg.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        cfg.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        cfg.setPeriodicidad(Periodicidad.MENSUAL);
        configRepository.save(cfg);
    }

    private PayrollPeriod createPeriod(UUID companyId) {
        PayrollPeriod pp = new PayrollPeriod();
        pp.setCompanyId(companyId);
        pp.setFechaInicio(LocalDate.of(2026, 7, 1));
        pp.setFechaFin(LocalDate.of(2026, 7, 31));
        pp.setEstado(EstadoPeriodo.ABIERTO);
        return periodRepository.save(pp);
    }
}
