package com.batulo.service.paiement;

import com.batulo.exception.MoosylIndisponibleException;
import com.batulo.exception.MoosylNonConfigureException;
import com.batulo.service.model.SessionMoosylModel;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Client HTTP de Moosyl (https://docs.moosyl.com). La clé secrète est envoyée telle quelle dans l'en-tête
 * Authorization (sans « Bearer ») ; elle n'apparaît jamais dans les journaux ni dans les messages d'erreur.
 */
@Service
public class MoosylHttpClient implements MoosylClient {

    private static final Logger log = LoggerFactory.getLogger(MoosylHttpClient.class);

    private final RestClient restClient;
    private final String cleApi;

    public MoosylHttpClient(@Value("${batulo.moosyl.url-base}") String urlBase,
                            @Value("${batulo.moosyl.cle-api}") String cleApi) {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(10));
        fabrique.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(urlBase).requestFactory(fabrique).build();
        this.cleApi = cleApi == null ? "" : cleApi.trim();
    }

    @Override
    public SessionMoosylModel creerSession(String transactionId, long montantMru, String telephone, String urlRetour,
                                           int expirationMinutes) {
        if (cleApi.isEmpty()) {
            throw new MoosylNonConfigureException(
                    "Le paiement mobile n'est pas configuré sur ce serveur (MOOSYL_API_KEY absente).");
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("transactionId", transactionId);
        corps.put("amount", montantMru);
        if (telephone != null) {
            corps.put("phoneNumber", telephone);
        }
        corps.put("successUrl", urlRetour);
        corps.put("cancelUrl", urlRetour);
        corps.put("expiresInMinutes", expirationMinutes);
        try {
            JsonNode reponse = restClient.post().uri("/checkout-session")
                    .header(HttpHeaders.AUTHORIZATION, cleApi)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corps)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = reponse == null ? null : reponse.path("data");
            String id = data == null ? "" : data.path("id").asText("");
            String checkoutUrl = reponse == null ? "" : reponse.path("checkoutUrl").asText("");
            if (id.isEmpty() || checkoutUrl.isEmpty()) {
                throw new MoosylIndisponibleException("Réponse inattendue de Moosyl à la création du paiement.");
            }
            return new SessionMoosylModel(id, checkoutUrl, date(data.path("expiresAt").asText(null)));
        } catch (RestClientResponseException e) {
            // Corps d'erreur de Moosyl en texte brut (« Invalid API key »...) : il ne contient jamais la clé.
            log.warn("Moosyl a refusé la création de la session {} : HTTP {} {}", transactionId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            String detail = e.getResponseBodyAsString().trim();
            throw new MoosylIndisponibleException("Moosyl a refusé le paiement (HTTP " + e.getStatusCode().value()
                    + ")" + (e.getStatusCode().is4xxClientError() && !detail.isEmpty() ? " : " + detail : "") + ".");
        } catch (RestClientException e) {
            log.warn("Moosyl injoignable pour la session {} : {}", transactionId, e.getClass().getSimpleName());
            throw new MoosylIndisponibleException("Moosyl ne répond pas : réessayez dans quelques instants.");
        }
    }

    @Override
    public Optional<String> statutSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        try {
            // Lecture publique de la session (vérifiée sur le sandbox : sans clé, réponse { data: { status } }).
            // La clé secrète n'est pas envoyée : cette route n'en a pas besoin.
            JsonNode reponse = restClient.get()
                    .uri("/checkout-session/public/" + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(JsonNode.class);
            if (reponse == null) {
                return Optional.empty();
            }
            String statut = reponse.path("data").path("status").asText(reponse.path("status").asText(""));
            return statut.isEmpty() ? Optional.empty() : Optional.of(statut.toLowerCase(Locale.ROOT));
        } catch (RestClientException e) {
            log.info("Relecture de la session Moosyl {} impossible ({}) : on attend le webhook", sessionId,
                    e instanceof RestClientResponseException r ? "HTTP " + r.getStatusCode().value()
                            : e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Instant date(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(texte);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
