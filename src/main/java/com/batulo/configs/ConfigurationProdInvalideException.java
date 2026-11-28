package com.batulo.configs;

import java.util.List;

/**
 * Profil « prod » : variables d'environnement absentes ou invalides, détectées avant la création du contexte.
 * Les problèmes sont tous listés en une fois ; aucune valeur (secret) n'est recopiée dans le message.
 */
public class ConfigurationProdInvalideException extends IllegalStateException {

    private final List<String> problemes;

    public ConfigurationProdInvalideException(List<String> problemes) {
        super("Profil prod : configuration incomplète ou invalide :\n - " + String.join("\n - ", problemes));
        this.problemes = List.copyOf(problemes);
    }

    public List<String> getProblemes() {
        return problemes;
    }
}
