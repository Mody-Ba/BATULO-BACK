package com.batulo.configs.security;

import com.batulo.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Écrit une ErrorResponse {code, message} depuis la chaîne de filtres de sécurité ou l'intercepteur,
 * là où le GlobalExceptionHandler n'intervient pas.
 */
@Component
public class ReponseErreurJson {

    private final ObjectMapper objectMapper;

    public ReponseErreurJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void ecrire(HttpServletResponse response, int statut, String code, String message) throws IOException {
        response.setStatus(statut);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
    }
}
