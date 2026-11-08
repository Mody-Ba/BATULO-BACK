package com.batulo.service.export;

import java.io.IOException;
import java.util.List;

/**
 * Écrit un tableau ligne par ligne, en flux. Valeurs acceptées : String, BigDecimal, Number, LocalDate,
 * LocalDateTime, ou null (cellule vide).
 */
public interface EcrivainTableau extends AutoCloseable {

    void entetes(List<String> entetes) throws IOException;

    void ligne(List<Object> valeurs) throws IOException;

    /** Termine le fichier (sans fermer le flux de sortie, qui appartient à l'appelant). */
    @Override
    void close() throws IOException;
}
