package com.batulo.service.model;

public record OptionResponse(
        Long id,
        String libelle,
        String valeur,
        int ordre
) {
}
