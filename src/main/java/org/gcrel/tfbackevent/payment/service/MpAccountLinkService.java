package org.gcrel.tfbackevent.payment.service;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MpAccountLinkService {
    private final OrganizerMpAccountRepository accountRepository;

    public MpAccountLinkService(OrganizerMpAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public MpAccountStatusResponse save(User user, String accessToken, String refreshToken,
            String mpUserId, String mpEmail, long expiresIn) {
        LocalDateTime now = LocalDateTime.now();
        OrganizerMpAccount account = accountRepository.findByUser(user)
                .orElse(new OrganizerMpAccount(user, accessToken, refreshToken, mpUserId, mpEmail,
                        now.plusSeconds(expiresIn)));

        account.setMpAccessToken(accessToken);
        account.setMpRefreshToken(refreshToken);
        account.setMpUserId(mpUserId);
        account.setMpEmail(mpEmail);
        account.setMpTokenExpiresAt(now.plusSeconds(expiresIn));
        account.setLinkedAt(now);

        accountRepository.save(account);
        return new MpAccountStatusResponse(true, account.getMpEmail(), account.getLinkedAt());
    }
}
