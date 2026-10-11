package org.gcrel.tfbackevent.payment.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.event.repository.EventRepository;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventRole;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.payment.exception.MpAccountHasTicketsException;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.exception.MpOAuthException;
import org.gcrel.tfbackevent.payment.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class MpOAuthService {
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final UserRepository userRepository;
    private final MpOAuthStateService oauthStateService;
    private final MpAccountLinkService accountLinkService;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int OAUTH_STATE_BYTES = 32;
    private static final long OAUTH_STATE_TTL_MINUTES = 10;
    private static final String MP_AUTH_URL = "https://auth.mercadopago.com/authorization";
    private static final String MP_TOKEN_URL = "https://api.mercadopago.com/oauth/token";

    @Value("${mercadopago.client-id}")
    private String clientId;

    @Value("${mercadopago.client-secret}")
    private String clientSecret;

    @Value("${mercadopago.redirect-uri}")
    private String redirectUri;

    public MpOAuthService(OrganizerMpAccountRepository mpAccountRepository, UserRepository userRepository,
            MpOAuthStateService oauthStateService, MpAccountLinkService accountLinkService,
            TicketRepository ticketRepository, EventRepository eventRepository) {
        this.mpAccountRepository = mpAccountRepository;
        this.userRepository = userRepository;
        this.oauthStateService = oauthStateService;
        this.accountLinkService = accountLinkService;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    // Construye la URL de autorización para redirigir al usuario a Mercado Pago y
    // obtener el código de autorización.
    public String buildAuthorizationUrl(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        String state = generateState();
        oauthStateService.create(state, user, LocalDateTime.now().plusMinutes(OAUTH_STATE_TTL_MINUTES));

        return MP_AUTH_URL +
                "?client_id=" + encode(clientId) +
                "&response_type=code" +
                "&platform_id=mp" +
                "&redirect_uri=" + encode(redirectUri) +
                "&state=" + encode(state);
    }

    private String generateState() {
        byte[] bytes = new byte[OAUTH_STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Intercambia el código de autorización por un access token y vincula la cuenta
    // de
    // Mercado Pago con el usuario en la base de datos.
    public MpAccountStatusResponse linkAccount(String code, String state) {
        User user = oauthStateService.consume(state);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    MP_TOKEN_URL, requestEntity, Map.class);

            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null || !responseBody.containsKey("access_token")) {
                throw new MpOAuthException("Respuesta inválida de MercadoPago.");
            }

            String accessToken = (String) responseBody.get("access_token");
            String mpUserId = responseBody.get("user_id").toString();

            // Obtener email de la cuenta MP
            String mpEmail = getMpEmail(accessToken, mpUserId);

            String refreshToken = (String) responseBody.get("refresh_token");
            Long expiresIn = ((Number) responseBody.get("expires_in")).longValue();

            return accountLinkService.save(user, accessToken, refreshToken, mpUserId, mpEmail, expiresIn);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new MpOAuthException(e.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Devuelve el estado de la cuenta de Mercado Pago vinculada al usuario.
    public MpAccountStatusResponse getAccountStatus(User user) {
        return mpAccountRepository.findByUser(user)
                .map(account -> new MpAccountStatusResponse(
                        true,
                        account.getMpEmail(),
                        account.getLinkedAt()))
                .orElse(new MpAccountStatusResponse());
    }

    // Desvincula la cuenta de Mercado Pago del usuario.
    @Transactional
    public void unlinkAccount(User user) {
        OrganizerMpAccount account = mpAccountRepository.findByUser(user)
                .orElseThrow(MpAccountNotLinkedException::new);
        if (ticketRepository.existsInPaidEventsByOrganizerAndStatusIn(
                user, EventRole.ORGANIZER, List.of(TicketStatus.PENDING, TicketStatus.ACTIVE))) {
            throw new MpAccountHasTicketsException();
        }
        eventRepository.findPaidPublishedWithoutOpenTickets(
                user,
                EventRole.ORGANIZER,
                EventStatus.PUBLISHED,
                List.of(TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED))
                .forEach(event -> {
                    event.setStatus(EventStatus.DRAFT);
                    eventRepository.save(event);
                });
        mpAccountRepository.delete(account);
    }

    // Devuelve un access token válido para la cuenta de Mercado Pago vinculada al
    // usuario.
    public String getValidAccessToken(User user) {
        OrganizerMpAccount account = mpAccountRepository.findByUser(user)
                .orElseThrow(MpAccountNotLinkedException::new);
        return getValidAccessToken(account);
    }

    // Sobrecarga del método para obtener un access token válido usando la cuenta de
    // Mercado Pago directamente.
    public String getValidAccessToken(OrganizerMpAccount account) {
        // Si el token expira en menos de 24 horas, renovarlo
        if (account.getMpTokenExpiresAt().isBefore(LocalDateTime.now().plusHours(24))) {
            refreshToken(account);
        }
        return account.getMpAccessToken();
    }

    // Obtiene el email de la cuenta de Mercado Pago usando el access token y el
    // user id.
    private String getMpEmail(String accessToken, String mpUserId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.mercadopago.com/users/" + mpUserId,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            Map<String, Object> body = response.getBody();
            return body != null ? (String) body.get("email") : "desconocido";

        } catch (Exception e) {
            return "desconocido";
        }
    }

    private void refreshToken(OrganizerMpAccount account) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", account.getMpRefreshToken());

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    MP_TOKEN_URL, requestEntity, Map.class);

            Map<String, Object> responseBody = response.getBody();

            account.setMpAccessToken((String) responseBody.get("access_token"));
            account.setMpRefreshToken((String) responseBody.get("refresh_token"));
            account.setMpTokenExpiresAt(
                    LocalDateTime.now().plusSeconds(
                            ((Number) responseBody.get("expires_in")).longValue()));
            // linkedAt conserva la fecha de vinculación: renovar el token no es volver a vincular.
            mpAccountRepository.save(account);

        } catch (Exception e) {
            throw new MpOAuthException("No se pudo renovar el token de MercadoPago.");
        }
    }
}
