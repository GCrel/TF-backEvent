package org.gcrel.tfbackevent.dto.response;

import lombok.Data;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private SystemRole systemRole;
    private LocalDate dateBirth;

    public UserResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.systemRole = user.getSystemRole();
        this.dateBirth = user.getDateBirth();
    }
}
