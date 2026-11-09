package org.gcrel.tfbackevent.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "mp_oauth_states")
public class MpOAuthState {
    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String state;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected MpOAuthState() {
    }

    public MpOAuthState(String state, User user, LocalDateTime expiresAt) {
        this.state = state;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public String getState() {
        return state;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
