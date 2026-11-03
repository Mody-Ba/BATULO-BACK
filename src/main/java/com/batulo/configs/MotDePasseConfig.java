package com.batulo.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Chiffrement des mots de passe avec BCrypt.
 * TEMPORAIRE - déplacé dans configs/security/WebSecurityConfig à l'étape 7.
 */
@Configuration
public class MotDePasseConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
