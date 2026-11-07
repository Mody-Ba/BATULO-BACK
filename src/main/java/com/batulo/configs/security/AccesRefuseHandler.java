package com.batulo.configs.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403 ACCES_REFUSE en JSON quand l'utilisateur est connecté mais n'a pas le bon rôle. */
@Component
public class AccesRefuseHandler implements AccessDeniedHandler {

    static final String MESSAGE = "Votre rôle ne permet pas d'accéder à cette ressource.";

    private final ReponseErreurJson reponseErreurJson;

    public AccesRefuseHandler(ReponseErreurJson reponseErreurJson) {
        this.reponseErreurJson = reponseErreurJson;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        reponseErreurJson.ecrire(response, HttpServletResponse.SC_FORBIDDEN, "ACCES_REFUSE", MESSAGE);
    }
}
