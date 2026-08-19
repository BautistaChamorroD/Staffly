package com.staffly.backend.payroll;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.CompanyRepository;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.payslip.PayslipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #147 (AUD-26, parte a): dos requests de cierre casi simultáneas contra
 * el mismo período no deben generar Payslips duplicados. Sin @Transactional a
 * propósito — cada hilo necesita su propia transacción real contra la base para
 * que el lock pesimista de {@link PayrollPeriodRepository#findByIdAndCompanyIdForUpdate}
 * tenga algo que serializar.
 */
@SpringBootTest
class PayrollPeriodCloseConcurrencyTest {

    @Autowired private PayrollPeriodCloseService closeService;
    @Autowired private CompanyRepository         companyRepository;
    @Autowired private BranchRepository          branchRepository;
    @Autowired private EmployeeRepository        employeeRepository;
    @Autowired private PayrollConfigRepository   configRepository;
    @Autowired private PayrollPeriodRepository   periodRepository;
    @Autowired private PayslipRepository         payslipRepository;

    @Test
    void dosCierresConcurrentes_soloUnoTieneExito_sinPayslipsDuplicados() throws Exception {
        UUID companyId = createCompany();
        Branch branch = createBranch(companyId);
        Employee employee = createEmployee(companyId, branch);
        createPayrollConfig(companyId);
        PayrollPeriod period = createPeriod(companyId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Boolean> attemptClose = () -> {
            ready.countDown();
            go.await(10, TimeUnit.SECONDS);
            try {
                closeService.close(period.getId(), companyId);
                return true;
            } catch (ConflictException ex) {
                return false;
            }
        };

        Future<Boolean> f1 = pool.submit(attemptClose);
        Future<Boolean> f2 = pool.submit(attemptClose);
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        boolean r1 = f1.get(15, TimeUnit.SECONDS);
        boolean r2 = f2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(r1 ^ r2).as("exactamente uno de los dos cierres debe tener éxito").isTrue();

        List<com.staffly.backend.payslip.Payslip> payslips =
                payslipRepository.findByCompanyIdAndEmployeeId(companyId, employee.getId());
        assertThat(payslips).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany() {
        Company c = new Company();
        c.setNombre("Empresa Concurrencia");
        c.setRazonSocial("Empresa Concurrencia SRL");
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

    private Employee createEmployee(UUID companyId, Branch branch) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre("Juan");
        e.setApellido("Pérez");
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(200_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
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
