package com.staffly.backend.security;

import com.staffly.backend.security.dto.ChangePasswordRequest;
import com.staffly.backend.security.dto.LoginRequest;
import com.staffly.backend.security.dto.LoginResponse;
import com.staffly.backend.security.dto.LogoutRequest;
import com.staffly.backend.security.dto.RefreshRequest;
import com.staffly.backend.security.dto.RefreshResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        authService.logout(request.refreshToken(), principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        authService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
