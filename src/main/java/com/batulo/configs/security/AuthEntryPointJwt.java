package com.batulo.configs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 NON_AUTHENTIFIE en JSON quand une route protégée est appelée sans jeton valide. */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private final ReponseErreurJson reponseErreurJson;

    public AuthEntryPointJwt(ReponseErreurJson reponseErreurJson) {
        this.reponseErreurJson = reponseErreurJson;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object raison = request.getAttribute(AuthTokenFilter.ATTRIBUT_REFUS);
        String message = raison != null ? raison.toString() : "Jeton d'authentification manquant.";
        reponseErreurJson.ecrire(response, HttpServletResponse.SC_UNAUTHORIZED, "NON_AUTHENTIFIE", message);
    }
}
