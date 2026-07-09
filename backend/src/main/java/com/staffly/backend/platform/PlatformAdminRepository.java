package com.staffly.backend.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByEmail(String email);
}
