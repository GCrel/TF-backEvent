package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
public class MpOAuthServiceTest {
    @Mock
    private OrganizerMpAccountRepository mpAccountRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MpOAuthService mpOAuthService;

    private User user;
    private OrganizerMpAccount account;
    private UUID userId;

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://misitio.com/oauth/callback";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mpOAuthService, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(mpOAuthService, "clientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(mpOAuthService, "redirectUri", REDIRECT_URI);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);

        account = new OrganizerMpAccount(user, "access_token_123", "refresh_token_123", "mp_userId_123", "test@mp.com",
                LocalDateTime.now().plusDays(30));
    }

    @Test
    void buildAuthorizationUrl_ReturnsCorrectUrl() {
        String url = mpOAuthService.buildAuthorizationUrl(userId);

        assertTrue(url.startsWith("https://auth.mercadopago.com/authorization"));
        assertTrue(url.contains("client_id=" + CLIENT_ID));
        assertTrue(url.contains("redirect_uri=" + REDIRECT_URI));
        assertTrue(url.contains("state=" + userId.toString()));
    }

    @Test
    void linkAccount_WhenSuccessful_ReturnsStatusResponseAndSavesAccount() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.empty());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "new_access_token");
        tokenResponseBody.put("user_id", 123456789);
        tokenResponseBody.put("refresh_token", "new_refresh_token");
        tokenResponseBody.put("expires_in", 3600); // 1 hora
        ResponseEntity<Map> tokenResponse = ResponseEntity.ok(tokenResponseBody);

        Map<String, Object> emailResponseBody = new HashMap<>();
        emailResponseBody.put("email", "organizer@mp.com");
        ResponseEntity<Map> emailResponse = ResponseEntity.ok(emailResponseBody);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(eq("https://api.mercadopago.com/oauth/token"), any(), eq(Map.class)))
                            .thenReturn(tokenResponse);
                    when(mock.exchange(contains("https://api.mercadopago.com/users/"), eq(HttpMethod.GET), any(),
                            eq(Map.class)))
                            .thenReturn(emailResponse);
                })) {

            var response = mpOAuthService.linkAccount("auth_code_123", userId);

            assertTrue(response.linked());
            assertEquals("organizer@mp.com", response.mpEmail());

            verify(mpAccountRepository).save(argThat(saved -> saved.getMpAccessToken().equals("new_access_token")
                    && saved.getMpRefreshToken().equals("new_refresh_token")
                    && saved.getMpUserId().equals("123456789")
                    && saved.getMpEmail().equals("organizer@mp.com")
                    && saved.getMpTokenExpiresAt().isAfter(LocalDateTime.now())));
            assertEquals(2, mocked.constructed().size());
        }

    }

    @Test
    void linkAccount_WhenAccountAlreadyExists_UpdatesAllOAuthCredentials() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.of(account));

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "updated_access_token");
        tokenResponseBody.put("user_id", 987654321);
        tokenResponseBody.put("refresh_token", "updated_refresh_token");
        tokenResponseBody.put("expires_in", 7200);
        ResponseEntity<Map> tokenResponse = ResponseEntity.ok(tokenResponseBody);

        Map<String, Object> emailResponseBody = new HashMap<>();
        emailResponseBody.put("email", "updated@mp.com");
        ResponseEntity<Map> emailResponse = ResponseEntity.ok(emailResponseBody);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(eq("https://api.mercadopago.com/oauth/token"), any(), eq(Map.class)))
                            .thenReturn(tokenResponse);
                    when(mock.exchange(contains("https://api.mercadopago.com/users/"), eq(HttpMethod.GET), any(),
                            eq(Map.class)))
                            .thenReturn(emailResponse);
                })) {
            mpOAuthService.linkAccount("new-auth-code", userId);
        }

        assertEquals("updated_access_token", account.getMpAccessToken());
        assertEquals("updated_refresh_token", account.getMpRefreshToken());
        assertEquals("987654321", account.getMpUserId());
        assertEquals("updated@mp.com", account.getMpEmail());
        assertTrue(account.getMpTokenExpiresAt().isAfter(LocalDateTime.now()));
        verify(mpAccountRepository).save(account);
    }

    @Test
    void linkAccount_WhenUserNotFound_ThrowsInvalidCredentialsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> mpOAuthService.linkAccount("auth_code_123", userId));
    }

    @Test
    void getAccountStatus_WhenLinked_ReturnsStatus() {
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.of(account));

        var response = mpOAuthService.getAccountStatus(user);

        assertTrue(response.linked());
        assertEquals("test@mp.com", response.mpEmail());
    }

    @Test
    void unlinkAccount_WhenLinked_DeletesAccount() {
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.of(account));

        mpOAuthService.unlinkAccount(user);

        verify(mpAccountRepository).delete(account);
    }

    @Test
    void unlinkAccount_WhenNotLinked_ThrowsException() {
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(MpAccountNotLinkedException.class, () -> mpOAuthService.unlinkAccount(user));
    }

    @Test
    void getValidAccessToken_WhenTokenIsValid_ReturnsCurrentToken() {
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.of(account));

        String token = mpOAuthService.getValidAccessToken(user);

        assertEquals("access_token_123", token);
        verify(mpAccountRepository, never()).save(any());
    }

    @Test
    void getValidAccessToken_WhenTokenIsExpiring_RefreshesToken() {
        account.setMpTokenExpiresAt(LocalDateTime.now().plusHours(2));
        when(mpAccountRepository.findByUser(user)).thenReturn(Optional.of(account));

        Map<String, Object> refreshResponseBody = new HashMap<>();
        refreshResponseBody.put("access_token", "refreshed_access_token");
        refreshResponseBody.put("refresh_token", "new_refresh_token");
        refreshResponseBody.put("expires_in", 3600);
        ResponseEntity<Map> refreshResponse = ResponseEntity.ok(refreshResponseBody);

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(eq("https://api.mercadopago.com/oauth/token"), any(), eq(Map.class)))
                            .thenReturn(refreshResponse);
                })) {

            String token = mpOAuthService.getValidAccessToken(user);

            assertEquals("refreshed_access_token", token);
            assertEquals("new_refresh_token", account.getMpRefreshToken());
            verify(mpAccountRepository).save(account);
        }
    }
}
