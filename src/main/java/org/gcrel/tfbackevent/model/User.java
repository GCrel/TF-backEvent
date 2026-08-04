package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;
import org.gcrel.tfbackevent.model.enums.SystemRole;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false)
    private SystemRole systemRole;

    @Column(name = "date_birth", nullable = false)
    private LocalDate dateBirth;


    public User() {}

    public User(String name, String email, String password, LocalDate dateBirth) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.systemRole = SystemRole.USER;
        this.dateBirth = dateBirth;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    public UUID getId() {
        return id;
    }
}
