package org.gcrel.tfbackevent.admin.dto.request;

import org.gcrel.tfbackevent.model.enums.SystemRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull(message = "Role is required")
        SystemRole role,

        // Contraseña de quien hace el cambio, no de la persona afectada.
        @NotBlank(message = "Password is required")
        String password) {
}
