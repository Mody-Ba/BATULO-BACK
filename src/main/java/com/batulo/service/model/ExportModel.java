package com.batulo.service.model;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Fichier d'export prêt à être envoyé : droits vérifiés et contenu préparé ; « contenu » l'écrit EN FLUX dans la
 * réponse HTTP (les réponses brutes sont lues au fur et à mesure, jamais toutes en mémoire).
 */
public record ExportModel(
        String nomFichier,
        String typeContenu,
        Contenu contenu
) {
    @FunctionalInterface
    public interface Contenu {
        void ecrire(OutputStream flux) throws IOException;
    }
}
