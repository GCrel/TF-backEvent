package org.gcrel.tfbackevent.payment.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.gcrel.tfbackevent.model.MpOAuthState;
import org.gcrel.tfbackevent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface MpOAuthStateRepository extends JpaRepository<MpOAuthState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MpOAuthState> findByStateAndExpiresAtAfter(String state, LocalDateTime now);

    void deleteByUser(User user);
}
