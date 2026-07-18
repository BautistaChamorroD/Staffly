package com.staffly.backend.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    void deleteByExpiraEnBefore(Instant limite);
}
