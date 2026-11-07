package com.batulo.configs.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Routes publiques, rôle exigé par préfixe d'URL, et jeton obligatoire pour tout le reste.
 * Comme dans ProxyMed, chaque méthode de controller porte aussi son @PreAuthorize. Les règles d'abonnement et de
 * mot de passe temporaire sont dans {@link AccesAbonnementInterceptor}, qui passe après ces contrôles.
 * Le CORS passe avant l'authentification : une pré-vérification OPTIONS du navigateur (sans jeton)
 * reçoit sa réponse CORS sans atteindre le 401.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    private final AuthEntryPointJwt authEntryPointJwt;
    private final AccesRefuseHandler accesRefuseHandler;
    private final AuthTokenFilter authTokenFilter;

    public WebSecurityConfig(AuthEntryPointJwt authEntryPointJwt, AccesRefuseHandler accesRefuseHandler,
                             AuthTokenFilter authTokenFilter) {
        this.authEntryPointJwt = authEntryPointJwt;
        this.accesRefuseHandler = accesRefuseHandler;
        this.authTokenFilter = authTokenFilter;
    }

    /**
     * Méthode statique : le chiffreur est disponible sans construire cette configuration, ce qui évite
     * le cycle WebSecurityConfig -> AuthTokenFilter -> AuthService -> PasswordEncoder.
     */
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthTokenFilter est un @Component : sans cela, Spring Boot l'enregistrerait aussi comme filtre servlet
     * global, en plus de sa place dans la chaîne de sécurité.
     */
    @Bean
    public FilterRegistrationBean<AuthTokenFilter> authTokenFilterRegistration(AuthTokenFilter filtre) {
        FilterRegistrationBean<AuthTokenFilter> enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.setEnabled(false);
        return enregistrement;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPointJwt)
                        .accessDeniedHandler(accesRefuseHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rendu interne des erreurs par Spring (pas une adresse publique).
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/inscription",
                                "/api/auth/connexion",
                                "/api/auth/rafraichir",
                                "/api/auth/mot-de-passe-oublie",
                                "/api/auth/reinitialiser-mot-de-passe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/abonnements/formules").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Rôles par préfixe : vérifiés ici, AVANT les règles d'abonnement de l'intercepteur, pour
                        // qu'un mauvais rôle donne toujours 403 ACCES_REFUSE. @PreAuthorize les répète sur chaque
                        // méthode des controllers (seconde barrière si une route change de préfixe).
                        .requestMatchers("/api/super-admin/**", "/api/dev/taches/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/**", "/api/abonnements/**", "/api/dev/paiements/**").hasRole("ADMIN")
                        .anyRequest().authenticated());

        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Origines autorisées : batulo.cors.origines (CORS_ORIGINS, séparées par des virgules), jamais « * ».
     * Pas de cookies : le jeton voyage dans l'en-tête Authorization.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${batulo.cors.origines}") String origines) {
        List<String> originesAutorisees = Arrays.stream(origines.split(","))
                .map(String::trim)
                .filter(origine -> !origine.isEmpty())
                .toList();
        if (originesAutorisees.isEmpty() || originesAutorisees.contains(CorsConfiguration.ALL)) {
            throw new IllegalStateException("CORS_ORIGINS doit lister des origines explicites (jamais \"*\")");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(originesAutorisees);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
