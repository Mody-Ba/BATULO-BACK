package com.batulo.configs.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Branche {@link LimiteRequetesFilter} sur les seules routes publiques, avant la sécurité. */
@Configuration
public class LimiteRequetesConfig {

    @Bean
    public FilterRegistrationBean<LimiteRequetesFilter> limiteRequetesFilter(
            @Value("${batulo.collecte.limite-lecture-par-minute}") int limiteLecture,
            @Value("${batulo.collecte.limite-envoi-par-minute}") int limiteEnvoi,
            @Value("${batulo.collecte.faire-confiance-proxy}") boolean faireConfianceProxy,
            ReponseErreurJson reponseErreurJson) {
        FilterRegistrationBean<LimiteRequetesFilter> enregistrement = new FilterRegistrationBean<>(
                new LimiteRequetesFilter(limiteLecture, limiteEnvoi, faireConfianceProxy, reponseErreurJson));
        enregistrement.addUrlPatterns("/api/public/*");
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return enregistrement;
    }
}
