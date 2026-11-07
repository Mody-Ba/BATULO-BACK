package com.batulo.service.model;

public record ResultatTacheResponse(
        String message,
        int abonnementsVerifies,
        int rappelsEnvoyes,
        int abonnementsExpires
) {
}
