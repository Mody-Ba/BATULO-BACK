package com.batulo.service.model;

import java.util.List;

/** Page de résultats renvoyée par les listes paginées. */
public record PageResponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long totalElements,
        int totalPages
) {
}
