package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;

import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User implements UserDetails {
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

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_code_expires_at")
    private LocalDateTime verificationCodeExpiresAt;

    public User() {}

    public User(String name, String email, String password, LocalDate dateBirth) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.systemRole = SystemRole.USER; // default role
        this.dateBirth = dateBirth;
        this.isEnabled = false; // default to not enabled until verified
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

    public boolean isEnabled() {
        return isEnabled;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public String getPassword() {
        return password;
    }

    public LocalDate getDateBirth() {
        return dateBirth;
    }

    public String getName() {
        return name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    public LocalDateTime getVerificationCodeExpiresAt() {
        return verificationCodeExpiresAt;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public void setVerificationCodeExpiresAt(LocalDateTime verificationCodeExpiresAt) {
        this.verificationCodeExpiresAt = verificationCodeExpiresAt;
    }
}
