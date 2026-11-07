package com.batulo.configs.security;

import com.batulo.configs.InfosJeton;
import com.batulo.configs.JwtUtils;
import com.batulo.service.AuthService;
import com.batulo.service.model.UtilisateurConnecteModel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Lit « Authorization: Bearer ... » et, si le jeton est valide, RECHARGE le compte en base : un compte supprimé
 * ou désactivé est refusé immédiatement, et le rôle ou l'abonnement est toujours celui du moment.
 * Sans jeton valide, la requête continue sans authentification : WebSecurityConfig répond alors 401 si la route
 * est protégée.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    /** Raison du refus, posée sur la requête pour le message du 401. */
    static final String ATTRIBUT_REFUS = AuthTokenFilter.class.getName() + ".refus";

    private static final String PREFIXE_BEARER = "Bearer ";

    private final JwtUtils jwtUtils;
    private final AuthService authService;

    public AuthTokenFilter(JwtUtils jwtUtils, AuthService authService) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jeton = lireJeton(request);
        if (jeton != null) {
            Optional<InfosJeton> infos = jwtUtils.verifierJeton(jeton);
            if (infos.isEmpty()) {
                request.setAttribute(ATTRIBUT_REFUS, "Jeton invalide ou expiré.");
            } else {
                Optional<UtilisateurConnecteModel> compte = authService.utilisateurConnecte(infos.get().utilisateurId());
                if (compte.isEmpty()) {
                    request.setAttribute(ATTRIBUT_REFUS, "Ce compte n'existe plus.");
                } else if (!compte.get().actif()) {
                    request.setAttribute(ATTRIBUT_REFUS, "Votre compte est désactivé.");
                } else {
                    authentifier(compte.get(), request);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Le rôle devient l'autorité « ROLE_xxx » vérifiée par @PreAuthorize("hasRole('...')"). */
    private void authentifier(UtilisateurConnecteModel compte, HttpServletRequest request) {
        UtilisateurConnecte principal = new UtilisateurConnecte(compte.id(), compte.courriel(), compte.role(),
                compte.entrepriseId(), compte.motDePasseTemporaire(), compte.statutAbonnement());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + compte.role().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String lireJeton(HttpServletRequest request) {
        String entete = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE_BEARER)) {
            return entete.substring(PREFIXE_BEARER.length()).trim();
        }
        return null;
    }
}
