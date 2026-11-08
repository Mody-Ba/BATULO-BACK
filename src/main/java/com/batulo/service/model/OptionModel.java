package com.batulo.service.model;

public record OptionModel(
        Long id,
        String libelle,
        String valeur,
        int ordre
) {
}
