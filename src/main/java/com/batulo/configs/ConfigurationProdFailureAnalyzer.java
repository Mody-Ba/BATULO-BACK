package com.batulo.configs;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Affiche « APPLICATION FAILED TO START » avec la liste des variables à corriger, sans trace Java. */
public class ConfigurationProdFailureAnalyzer extends AbstractFailureAnalyzer<ConfigurationProdInvalideException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConfigurationProdInvalideException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "Définissez ou corrigez ces variables d'environnement (Heroku : heroku config:set NOM=valeur), "
                        + "puis redémarrez. Liste complète : docs/deploiement-heroku.md.",
                cause);
    }
}
