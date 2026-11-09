package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.MpOAuthState;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.exception.InvalidMpOAuthStateException;
import org.gcrel.tfbackevent.payment.repository.MpOAuthStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MpOAuthStateService {
    private final MpOAuthStateRepository stateRepository;

    public MpOAuthStateService(MpOAuthStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @Transactional
    public void create(String state, User user, LocalDateTime expiresAt) {
        stateRepository.deleteByUser(user);
        stateRepository.save(new MpOAuthState(state, user, expiresAt));
    }

    @Transactional
    public User consume(String state) {
        MpOAuthState oauthState = stateRepository
                .findByStateAndExpiresAtAfter(state, LocalDateTime.now())
                .orElseThrow(InvalidMpOAuthStateException::new);
        stateRepository.delete(oauthState);
        return oauthState.getUser();
    }
}
