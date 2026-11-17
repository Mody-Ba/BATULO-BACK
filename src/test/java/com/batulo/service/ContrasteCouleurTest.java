package com.batulo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Couleur de texte lisible (contraste WCAG 2.x) et détection du vrai type d'un logo. */
class ContrasteCouleurTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "#FFFFFF, #000000",
            "#000000, #FFFFFF",
            "#FFFF00, #000000",   // jaune : très clair
            "#000080, #FFFFFF",   // bleu marine
            "#005CBB, #FFFFFF",   // couleur Batulo par défaut
            "#1E6B52, #FFFFFF",
            "#FF0000, #000000",   // rouge pur : 5,25 avec le noir contre 4,0 avec le blanc
            "#777777, #000000",   // gris moyen : 4,69 avec le noir contre 4,48 avec le blanc
            "#767676, #000000",   // 4,54 avec le blanc, mais 4,62 avec le noir : le plus contrasté gagne
            "#6F6F6F, #FFFFFF",   // un peu plus sombre : le blanc l'emporte
            "#F5A623, #000000"})
    void couleurTexteSelonLeContraste(String fond, String attendu) {
        assertThat(ContrasteCouleur.couleurTexte(fond)).isEqualTo(attendu);
    }

    @Test
    void rapportsDeContrasteWcag() {
        assertThat(ContrasteCouleur.contraste("#FFFFFF", "#000000")).isCloseTo(21.0, within(0.001));
        assertThat(ContrasteCouleur.contraste("#767676", "#FFFFFF")).isCloseTo(4.54, within(0.01));
        assertThat(ContrasteCouleur.contraste("#FF0000", "#000000")).isCloseTo(5.25, within(0.01));
        assertThat(ContrasteCouleur.luminance("#808080")).isCloseTo(0.2159, within(0.0001));
    }

    @Test
    void formatHexadecimal() {
        assertThat(ContrasteCouleur.valide("#1e6b52")).isTrue();
        assertThat(ContrasteCouleur.normaliser("#1e6b52")).isEqualTo("#1E6B52");
        for (String invalide : new String[]{"1E6B52", "#1E6B5", "#1E6B520", "#GGGGGG", "red", "#fff", "", " #1E6B52"}) {
            assertThat(ContrasteCouleur.valide(invalide)).as(invalide).isFalse();
        }
        assertThat(ContrasteCouleur.valide(null)).isFalse();
    }

    @Test
    void vraiTypeParLesPremiersOctets() {
        assertThat(ValidateurLogo.typeReel(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}))
                .isEqualTo("image/png");
        assertThat(ValidateurLogo.typeReel(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .isEqualTo("image/jpeg");
        assertThat(ValidateurLogo.typeReel("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                .isNull();
        assertThat(ValidateurLogo.typeReel("GIF89a".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(ValidateurLogo.typeReel("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(ValidateurLogo.typeReel(new byte[]{(byte) 0x89, 'P', 'N'})).isNull();
    }
}
