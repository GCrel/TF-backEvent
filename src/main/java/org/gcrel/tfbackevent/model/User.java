package org.gcrel.tfbackevent.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.CodePurpose;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(name = "verification_code_failed_attempts", nullable = false)
    private int verificationCodeFailedAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_purpose")
    private CodePurpose codePurpose;

    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "avatar_key")
    private String avatarKey;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, String password, LocalDate dateBirth) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.systemRole = SystemRole.USER; // default role
        this.dateBirth = dateBirth;
        this.isEnabled = false; // default to not enabled until verified
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
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

    public CodePurpose getCodePurpose() {
        return codePurpose;
    }

    public void setCodePurpose(CodePurpose codePurpose) {
        this.codePurpose = codePurpose;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void block(String reason, LocalDateTime at) {
        this.blocked = true;
        this.blockedReason = reason;
        this.blockedAt = at;
    }

    public void unblock() {
        this.blocked = false;
        this.blockedReason = null;
        this.blockedAt = null;
    }

    public void setSystemRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !blocked;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public int getVerificationCodeFailedAttempts() {
        return verificationCodeFailedAttempts;
    }

    public void setVerificationCodeFailedAttempts(int verificationCodeFailedAttempts) {
        this.verificationCodeFailedAttempts = verificationCodeFailedAttempts;
    }
}
