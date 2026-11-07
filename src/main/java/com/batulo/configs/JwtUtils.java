package com.batulo.configs;

import com.batulo.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Seul endroit qui crée et vérifie les jetons d'accès (JWT signés HS256 avec JWT_SECRET).
 * Le reste du code ne manipule que {@link InfosJeton} et une chaîne opaque.
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    static final String CLAIM_ROLE = "role";

    /** HS256 exige une clé d'au moins 256 bits. */
    private static final int TAILLE_MIN_SECRET_OCTETS = 32;

    @Value("${batulo.jwt.secret}")
    private String jwtSecret;

    @Value("${batulo.jwt.emetteur}")
    private String emetteur;

    @Value("${batulo.jwt.duree-acces-minutes}")
    private long dureeAccesMinutes;

    private SecretKey cle;

    @PostConstruct
    void init() {
        byte[] secret = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < TAILLE_MIN_SECRET_OCTETS) {
            throw new IllegalStateException("JWT_SECRET doit faire au moins " + TAILLE_MIN_SECRET_OCTETS
                    + " octets pour signer en HS256 (voir .env.properties)");
        }
        this.cle = Keys.hmacShaKeyFor(secret);
    }

    public String genererJeton(Long utilisateurId, Role role) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .issuer(emetteur)
                .subject(String.valueOf(utilisateurId))
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(dureeAcces())))
                // Algorithme explicite : sinon jjwt choisit HS384/HS512 selon la longueur du secret.
                .signWith(cle, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Vérifie la signature, l'algorithme, l'émetteur et l'expiration.
     *
     * @return le contenu du jeton, ou vide s'il est invalide, falsifié ou expiré
     */
    public Optional<InfosJeton> verifierJeton(String jeton) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(cle)
                    .requireIssuer(emetteur)
                    .build()
                    .parseSignedClaims(jeton);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                log.debug("Jeton refusé : algorithme {} non accepté", jws.getHeader().getAlgorithm());
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            return Optional.of(new InfosJeton(
                    Long.valueOf(claims.getSubject()),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // Le message ne contient jamais le jeton lui-même.
            log.debug("Jeton refusé : {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Duration dureeAcces() {
        return Duration.ofMinutes(dureeAccesMinutes);
    }
}
