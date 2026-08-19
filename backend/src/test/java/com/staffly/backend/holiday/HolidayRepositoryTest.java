package com.staffly.backend.holiday;

import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class HolidayRepositoryTest {

    @Autowired private HolidayRepository holidayRepository;
    @Autowired private EntityManager entityManager;

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

    private void createHoliday(UUID companyId, LocalDate fecha, String nombre, boolean recurrente) {
        Holiday h = new Holiday();
        h.setCompanyId(companyId);
        h.setFecha(fecha);
        h.setNombre(nombre);
        h.setRecurrente(recurrente);
        entityManager.persist(h);
        entityManager.flush();
    }

    @Test
    void feriadoRecurrenteSeAplicaEnAnioPosterior() {
        UUID companyId = createCompany();
        createHoliday(companyId, LocalDate.of(2026, 12, 25), "Navidad", true);

        List<LocalDate> aplicables = holidayRepository.findApplicableFechasInRange(
                companyId, LocalDate.of(2027, 12, 1), LocalDate.of(2027, 12, 31));

        assertThat(aplicables).containsExactly(LocalDate.of(2027, 12, 25));
    }

    @Test
    void feriadoNoRecurrenteNoSeAplicaEnOtroAnio() {
        UUID companyId = createCompany();
        createHoliday(companyId, LocalDate.of(2026, 12, 25), "Feriado puntual", false);

        List<LocalDate> aplicables = holidayRepository.findApplicableFechasInRange(
                companyId, LocalDate.of(2027, 12, 1), LocalDate.of(2027, 12, 31));

        assertThat(aplicables).isEmpty();
    }

    @Test
    void feriadoNoRecurrenteSeAplicaEnSuPropioAnio() {
        UUID companyId = createCompany();
        createHoliday(companyId, LocalDate.of(2026, 12, 25), "Feriado puntual", false);

        List<LocalDate> aplicables = holidayRepository.findApplicableFechasInRange(
                companyId, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));

        assertThat(aplicables).containsExactly(LocalDate.of(2026, 12, 25));
    }

    @Test
    void feriadoRecurrenteFueraDelRangoNoSeIncluye() {
        UUID companyId = createCompany();
        createHoliday(companyId, LocalDate.of(2026, 12, 25), "Navidad", true);

        List<LocalDate> aplicables = holidayRepository.findApplicableFechasInRange(
                companyId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));

        assertThat(aplicables).isEmpty();
    }
}
