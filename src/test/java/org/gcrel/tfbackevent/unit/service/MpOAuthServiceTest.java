package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.payment.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.payment.exception.InvalidMpOAuthStateException;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.service.MpAccountLinkService;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.gcrel.tfbackevent.payment.service.MpOAuthStateService;
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
    @Mock
    private MpOAuthStateService oauthStateService;
    @Mock
    private MpAccountLinkService accountLinkService;
    @Mock
    private EventRepository eventRepository;

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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String url = mpOAuthService.buildAuthorizationUrl(userId);

        assertTrue(url.startsWith("https://auth.mercadopago.com/authorization"));
        assertTrue(url.contains("client_id=" + CLIENT_ID));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fmisitio.com%2Foauth%2Fcallback"));
        String state = url.substring(url.indexOf("state=") + "state=".length());
        assertEquals(43, state.length());
        assertTrue(state.matches("[A-Za-z0-9_-]+"));
        org.mockito.Mockito.verify(oauthStateService).create(eq(state), eq(user), any(LocalDateTime.class));
    }

    @Test
    void linkAccount_WhenSuccessful_ReturnsStatusResponseAndSavesAccount() {
        when(oauthStateService.consume("state")).thenReturn(user);
        when(accountLinkService.save(eq(user), eq("new_access_token"), eq("new_refresh_token"),
                eq("123456789"), eq("organizer@mp.com"), eq(3600L)))
                .thenReturn(new MpAccountStatusResponse(true, "organizer@mp.com", LocalDateTime.now()));

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

            var response = mpOAuthService.linkAccount("auth_code_123", "state");

            assertTrue(response.linked());
            assertEquals("organizer@mp.com", response.mpEmail());

            assertEquals(2, mocked.constructed().size());
        }

    }

    @Test
    void linkAccount_WhenAccountAlreadyExists_UpdatesAllOAuthCredentials() {
        when(oauthStateService.consume("state")).thenReturn(user);

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
            when(accountLinkService.save(eq(user), eq("updated_access_token"), eq("updated_refresh_token"),
                    eq("987654321"), eq("updated@mp.com"), eq(7200L)))
                    .thenReturn(new MpAccountStatusResponse(true, "updated@mp.com", LocalDateTime.now()));
            mpOAuthService.linkAccount("new-auth-code", "state");
        }

        org.mockito.Mockito.verify(accountLinkService).save(eq(user), eq("updated_access_token"),
                eq("updated_refresh_token"), eq("987654321"), eq("updated@mp.com"), eq(7200L));
    }

    @Test
    void linkAccount_WhenUserNotFound_ThrowsInvalidCredentialsException() {
        when(oauthStateService.consume("state")).thenThrow(new InvalidMpOAuthStateException());

        assertThrows(InvalidMpOAuthStateException.class, () -> mpOAuthService.linkAccount("auth_code_123", "state"));
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
        Event publishedEvent = org.mockito.Mockito.mock(Event.class);
        when(eventRepository.findByOrganizerAndStatus(
                user, EventRole.ORGANIZER, EventStatus.PUBLISHED))
                .thenReturn(java.util.List.of(publishedEvent));

        mpOAuthService.unlinkAccount(user);

        verify(publishedEvent).setStatus(EventStatus.DRAFT);
        verify(eventRepository).save(publishedEvent);
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
