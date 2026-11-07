package com.batulo.configs.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Branche {@link AccesAbonnementInterceptor} sur toutes les routes de l'API. */
@Configuration
public class WebMvcSecuriteConfig implements WebMvcConfigurer {

    private final AccesAbonnementInterceptor accesAbonnementInterceptor;

    public WebMvcSecuriteConfig(AccesAbonnementInterceptor accesAbonnementInterceptor) {
        this.accesAbonnementInterceptor = accesAbonnementInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accesAbonnementInterceptor).addPathPatterns("/api/**");
    }
}
