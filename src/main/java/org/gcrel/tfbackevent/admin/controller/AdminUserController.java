package org.gcrel.tfbackevent.admin.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.request.BlockUserRequest;
import org.gcrel.tfbackevent.admin.dto.request.ChangeRoleRequest;
import org.gcrel.tfbackevent.admin.dto.response.UserAdminResponse;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<Page<UserAdminResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SystemRole role,
            @RequestParam(required = false) Boolean blocked,
            Pageable pageable) {
        return ResponseEntity.ok(adminUserService.search(q, role, blocked, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserAdminResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.get(id));
    }

    @PatchMapping("/{id}/block")
    public ResponseEntity<UserAdminResponse> block(
            @PathVariable UUID id,
            @Validated @RequestBody BlockUserRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(adminUserService.block(actor, id, request.reason()));
    }

    @PatchMapping("/{id}/unblock")
    public ResponseEntity<UserAdminResponse> unblock(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(adminUserService.unblock(actor, id));
    }

    @PostMapping("/{id}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        adminUserService.revokeSessions(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserAdminResponse> changeRole(
            @PathVariable UUID id,
            @Validated @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(adminUserService.changeRole(actor, id, request.role(), request.password()));
    }
}
