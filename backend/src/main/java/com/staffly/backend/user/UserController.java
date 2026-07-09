package com.staffly.backend.user;

import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.dto.CreateUserRequest;
import com.staffly.backend.user.dto.CreateUserResponse;
import com.staffly.backend.user.dto.UpdateUserRequest;
import com.staffly.backend.user.dto.UpdateUserStatusRequest;
import com.staffly.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(required = false) RolUsuario rol,
            @RequestParam(required = false) EstadoUsuario estado) {
        return ResponseEntity.ok(userService.list(rol, estado));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(userService.getMe(principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(
            @PathVariable UUID id, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(userService.getById(id, principal));
    }

    @PostMapping
    public ResponseEntity<CreateUserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        CreateUserResponse response = userService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.user().id())).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(userService.update(id, request, principal));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(userService.updateStatus(id, request, principal));
    }
}
