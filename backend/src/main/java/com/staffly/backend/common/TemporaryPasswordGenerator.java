package com.staffly.backend.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera contraseñas provisorias para altas de cuentas (Company -> Admin
 * inicial en BE-1.3, User en BE-1.5). Se devuelven en texto plano una sola
 * vez en la respuesta del alta — no hay envío de email en v1.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
