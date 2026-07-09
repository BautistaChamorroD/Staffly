package com.staffly.backend.branch;

import com.staffly.backend.branch.dto.BranchResponse;
import com.staffly.backend.branch.dto.CreateBranchRequest;
import com.staffly.backend.branch.dto.UpdateBranchRequest;
import com.staffly.backend.branch.dto.UpdateBranchStatusRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public ResponseEntity<List<BranchResponse>> list() {
        return ResponseEntity.ok(branchService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchResponse> getById(
            @PathVariable UUID id, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(branchService.getById(id, principal));
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(
            @Valid @RequestBody CreateBranchRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        BranchResponse response = branchService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/branches/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BranchResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBranchRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(branchService.update(id, request, principal));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BranchResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBranchStatusRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(branchService.updateStatus(id, request, principal));
    }
}
