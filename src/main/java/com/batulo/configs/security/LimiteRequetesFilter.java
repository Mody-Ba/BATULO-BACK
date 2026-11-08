package com.batulo.configs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite simple contre l'abus des routes publiques (/api/public/**) : nombre de requêtes par adresse IP et par
 * minute (fenêtre fixe), séparément pour la lecture (GET) et l'envoi (POST). Au-delà : 429 TROP_DE_REQUETES avec
 * Retry-After. Compteurs en mémoire : valables pour une seule instance de l'application.
 */
public class LimiteRequetesFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = 60_000;
    private static final int TAILLE_NETTOYAGE = 50_000;

    private final int limiteLecture;
    private final int limiteEnvoi;
    /** true seulement derrière un proxy de confiance : l'adresse vient alors de X-Forwarded-For. */
    private final boolean faireConfianceProxy;
    private final ReponseErreurJson reponseErreurJson;
    private final ConcurrentHashMap<String, Fenetre> fenetres = new ConcurrentHashMap<>();

    private record Fenetre(long minute, AtomicInteger compteur) {
    }

    public LimiteRequetesFilter(int limiteLecture, int limiteEnvoi, boolean faireConfianceProxy,
                                ReponseErreurJson reponseErreurJson) {
        this.limiteLecture = limiteLecture;
        this.limiteEnvoi = limiteEnvoi;
        this.faireConfianceProxy = faireConfianceProxy;
        this.reponseErreurJson = reponseErreurJson;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        boolean lecture = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        long maintenant = System.currentTimeMillis();
        long minute = maintenant / MINUTE_MS;
        String cle = adresse(request) + (lecture ? "|L" : "|E");

        if (fenetres.size() > TAILLE_NETTOYAGE) {
            fenetres.values().removeIf(f -> f.minute() != minute);
        }
        Fenetre fenetre = fenetres.compute(cle, (k, f) -> f == null || f.minute() != minute
                ? new Fenetre(minute, new AtomicInteger()) : f);
        if (fenetre.compteur().incrementAndGet() > (lecture ? limiteLecture : limiteEnvoi)) {
            long secondes = Math.max(1, ((minute + 1) * MINUTE_MS - maintenant + 999) / 1000);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondes));
            reponseErreurJson.ecrire(response, 429, "TROP_DE_REQUETES",
                    "Trop de requêtes depuis votre adresse : réessayez dans " + secondes + " secondes.");
            return;
        }
        chain.doFilter(request, response);
    }

    private String adresse(HttpServletRequest request) {
        if (faireConfianceProxy) {
            String transmise = request.getHeader("X-Forwarded-For");
            if (transmise != null && !transmise.isBlank()) {
                return transmise.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
