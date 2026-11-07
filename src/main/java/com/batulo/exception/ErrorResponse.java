package com.batulo.exception;

/**
 * Format unique des erreurs de l'API Batulo : un code stable (pour le frontend) et un message lisible.
 */
public record ErrorResponse(
        String code,
        String message
) {
}
