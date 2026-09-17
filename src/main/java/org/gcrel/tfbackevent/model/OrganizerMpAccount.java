package org.gcrel.tfbackevent.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizer_mp_accounts")
public class OrganizerMpAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "mp_access_token", nullable = false)
    private String mpAccessToken;

    @Column(name = "mp_user_id", nullable = false)
    private String mpUserId;

    @Column(name = "mp_email", nullable = false)
    private String mpEmail;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Column(name = "mp_refresh_token", nullable = false)
    private String mpRefreshToken;

    @Column(name = "mp_token_expires_at", nullable = false)
    private LocalDateTime mpTokenExpiresAt;

    protected OrganizerMpAccount() {
    }

    public OrganizerMpAccount(User user, String mpAccessToken, String mpRefreshToken, String mpUserId, String mpEmail,
            LocalDateTime mpTokenExpiresAt) {
        this.user = user;
        this.mpAccessToken = mpAccessToken;
        this.mpRefreshToken = mpRefreshToken;
        this.mpUserId = mpUserId;
        this.mpEmail = mpEmail;
        this.mpTokenExpiresAt = mpTokenExpiresAt;
        this.linkedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getMpAccessToken() {
        return mpAccessToken;
    }

    public String getMpUserId() {
        return mpUserId;
    }

    public String getMpEmail() {
        return mpEmail;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public String getMpRefreshToken() {
        return mpRefreshToken;
    }

    public LocalDateTime getMpTokenExpiresAt() {
        return mpTokenExpiresAt;
    }

    public void setMpAccessToken(String mpAccessToken) {
        this.mpAccessToken = mpAccessToken;
    }

    public void setMpUserId(String mpUserId) {
        this.mpUserId = mpUserId;
    }

    public void setMpEmail(String mpEmail) {
        this.mpEmail = mpEmail;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public void setMpRefreshToken(String mpRefreshToken) {
        this.mpRefreshToken = mpRefreshToken;
    }

    public void setMpTokenExpiresAt(LocalDateTime mpTokenExpiresAt) {
        this.mpTokenExpiresAt = mpTokenExpiresAt;
    }
}
