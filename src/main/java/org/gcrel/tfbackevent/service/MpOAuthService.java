package org.gcrel.tfbackevent.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.exception.MpOAuthException;
import org.gcrel.tfbackevent.model.OrganizerMpAccount;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.repository.OrganizerMpAccountRepository;
import org.gcrel.tfbackevent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class MpOAuthService {
    private final OrganizerMpAccountRepository mpAccountRepository;
    private final UserRepository userRepository;

    @Value("${mercadopago.client-id}")
    private String clientId;

    @Value("${mercadopago.client-secret}")
    private String clientSecret;

    @Value("${mercadopago.redirect-uri}")
    private String redirectUri;

    private static final String MP_AUTH_URL = "https://auth.mercadopago.com/authorization";
    private static final String MP_TOKEN_URL = "https://api.mercadopago.com/oauth/token";

    public MpOAuthService(OrganizerMpAccountRepository mpAccountRepository, UserRepository userRepository) {
        this.mpAccountRepository = mpAccountRepository;
        this.userRepository = userRepository;
    }

    // Construye la URL de autorización para redirigir al usuario a Mercado Pago y
    // obtener el código de autorización.
    public String buildAuthorizationUrl(UUID userId) {
        return MP_AUTH_URL +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&platform_id=mp" +
                "&redirect_uri=" + redirectUri +
                "&state=" + userId;
    }

    // Intercambia el código de autorización por un access token y vincula la cuenta
    // de
    // Mercado Pago con el usuario en la base de datos.
    public MpAccountStatusResponse linkAccount(String code, UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

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

            // Si ya tiene cuenta vinculada, actualizarla
            OrganizerMpAccount account = mpAccountRepository.findByUser(user)
                    .orElse(new OrganizerMpAccount(user, accessToken, refreshToken, mpUserId, mpEmail,
                            LocalDateTime.now().plusSeconds(expiresIn)));

            account.setMpAccessToken(accessToken);
            account.setMpRefreshToken(refreshToken);
            account.setMpUserId(mpUserId);
            account.setMpEmail(mpEmail);
            account.setMpTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            account.setLinkedAt(LocalDateTime.now());

            mpAccountRepository.save(account);
            return new MpAccountStatusResponse(true, account.getMpEmail(), account.getLinkedAt());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new MpOAuthException(e.getMessage());
        }
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
    public void unlinkAccount(User user) {
        OrganizerMpAccount account = mpAccountRepository.findByUser(user)
                .orElseThrow(MpAccountNotLinkedException::new);
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
            account.setLinkedAt(LocalDateTime.now());
            mpAccountRepository.save(account);

        } catch (Exception e) {
            throw new MpOAuthException("No se pudo renovar el token de MercadoPago.");
        }
    }
}
