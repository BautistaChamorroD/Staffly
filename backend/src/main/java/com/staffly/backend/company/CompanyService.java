package com.staffly.backend.company;

import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.company.dto.CompanyResponse;
import com.staffly.backend.company.dto.CreateCompanyRequest;
import com.staffly.backend.company.dto.CreateCompanyResponse;
import com.staffly.backend.company.dto.UpdateCompanyRequest;
import com.staffly.backend.company.dto.UpdateCompanyStatusRequest;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.RolUsuario;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public CompanyService(CompanyRepository companyRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return companyRepository.findAll().stream()
                .map(CompanyResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID id) {
        return CompanyResponse.from(findCompanyOrThrow(id));
    }

    @Transactional
    public CreateCompanyResponse create(CreateCompanyRequest request) {
        if (userRepository.findByEmail(request.adminEmail()).isPresent()) {
            throw new ConflictException("El email ya está en uso");
        }

        Company company = new Company();
        company.setNombre(request.nombre());
        company.setRazonSocial(request.razonSocial());
        company.setPais(request.pais());
        company.setMoneda(request.moneda());
        company.setZonaHoraria(request.zonaHoraria());
        company.setPlan(request.plan());
        company.setEstado(EstadoEmpresa.ACTIVA);
        company = companyRepository.save(company);

        String temporaryPassword = generateTemporaryPassword();

        User admin = new User();
        admin.setCompanyId(company.getId());
        admin.setEmail(request.adminEmail());
        admin.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        admin.setRol(RolUsuario.ADMIN);
        admin.setEstado(EstadoUsuario.ACTIVO);
        admin.setDebeCambiarPassword(true);
        userRepository.save(admin);

        return new CreateCompanyResponse(CompanyResponse.from(company), request.adminEmail(), temporaryPassword);
    }

    @Transactional
    public CompanyResponse update(UUID id, UpdateCompanyRequest request) {
        Company company = findCompanyOrThrow(id);

        if (request.nombre() != null) {
            company.setNombre(request.nombre());
        }
        if (request.razonSocial() != null) {
            company.setRazonSocial(request.razonSocial());
        }
        if (request.pais() != null) {
            company.setPais(request.pais());
        }
        if (request.moneda() != null) {
            company.setMoneda(request.moneda());
        }
        if (request.zonaHoraria() != null) {
            company.setZonaHoraria(request.zonaHoraria());
        }
        if (request.plan() != null) {
            company.setPlan(request.plan());
        }

        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse updateStatus(UUID id, UpdateCompanyStatusRequest request) {
        Company company = findCompanyOrThrow(id);
        company.setEstado(request.estado());
        return CompanyResponse.from(companyRepository.save(company));
    }

    private Company findCompanyOrThrow(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la empresa solicitada"));
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
