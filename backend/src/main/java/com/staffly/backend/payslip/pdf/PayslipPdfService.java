package com.staffly.backend.payslip.pdf;

import com.staffly.backend.common.pdf.PdfExportAdapter;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.CompanyRepository;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.payslip.PayslipService;
import com.staffly.backend.payslip.dto.PayslipResponse;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class PayslipPdfService {

    private final PayslipService               payslipService;
    private final CompanyRepository            companyRepository;
    private final EmployeeRepository           employeeRepository;
    private final PdfExportAdapter<PayslipPdfData> pdfAdapter;

    public PayslipPdfService(PayslipService payslipService,
                             CompanyRepository companyRepository,
                             EmployeeRepository employeeRepository,
                             PdfExportAdapter<PayslipPdfData> pdfAdapter) {
        this.payslipService    = payslipService;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.pdfAdapter        = pdfAdapter;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID payslipId, StafflyUserPrincipal principal) {
        PayslipResponse payslip = payslipService.getById(payslipId, principal);

        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la empresa"));

        Employee employee = employeeRepository
                .findByIdAndCompanyId(payslip.employeeId(), principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado"));

        PayslipPdfData data = new PayslipPdfData(
                company.getNombre(),
                company.getRazonSocial(),
                employee.getNombre(),
                employee.getApellido(),
                employee.getDocumento(),
                employee.getCategoria(),
                payslip.periodoInicio(),
                payslip.periodoFin(),
                payslip.id(),
                payslip.estado(),
                payslip.tipo(),
                payslip.payslipOriginalId(),
                payslip.sueldoBase(),
                payslip.horasNormales(),
                payslip.horasExtra(),
                payslip.horasFeriado(),
                payslip.brutoCalculado(),
                payslip.detalleDescuentos(),
                payslip.totalDeducciones(),
                payslip.totalAdelantos(),
                payslip.netoFinal(),
                payslip.fechaPago(),
                LocalDate.now()
        );

        return pdfAdapter.generate(data);
    }
}
