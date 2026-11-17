package com.batulo.controller;

import com.batulo.service.PersonnalisationService;
import com.batulo.service.model.LogoPublicModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Tag(name = "Formulaire public", description = "Routes du répondant, sans authentification")
public class LogoPublicController {

    private final PersonnalisationService personnalisationService;

    public LogoPublicController(PersonnalisationService personnalisationService) {
        this.personnalisationService = personnalisationService;
    }

    @GetMapping("/api/public/logos/{jeton}")
    @Operation(summary = "Logo d'une entreprise",
            description = "Lecture seule, sans authentification. Le jeton est non devinable et change à chaque "
                    + "remplacement : réponse mise en cache un an (immutable). 404 LOGO_INTROUVABLE sinon.")
    public ResponseEntity<byte[]> logo(@PathVariable String jeton) {
        LogoPublicModel logo = personnalisationService.logoPublic(jeton);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.typeMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(logo.contenu());
    }
}
