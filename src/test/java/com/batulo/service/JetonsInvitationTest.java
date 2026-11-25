package com.batulo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Jetons des liens d'invitation : secret obligatoire, même lien pour un même sel, rien d'exploitable en base. */
class JetonsInvitationTest {

    private static final String SECRET = "secret-de-test-des-invitations-au-moins-32-octets";

    @Test
    void demarrageRefuseSansSecretSuffisant() {
        for (String secret : new String[]{null, "", "trop-court"}) {
            JetonsInvitation jetons = new JetonsInvitation(secret);
            assertThatThrownBy(jetons::init).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVITATION_SECRET");
        }
    }

    @Test
    void jetonStableParSelEtDifferentSelonLeSecret() {
        JetonsInvitation jetons = new JetonsInvitation(SECRET);
        jetons.init();
        String sel = jetons.nouveauSel();
        String jeton = jetons.jeton(sel);
        assertThat(jeton).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(sel);
        assertThat(jetons.jeton(sel)).isEqualTo(jeton);
        assertThat(jetons.jeton(jetons.nouveauSel())).isNotEqualTo(jeton);
        assertThat(jetons.hashDepuisSel(sel)).isEqualTo(jetons.hash(jeton)).hasSize(64);

        JetonsInvitation autreSecret = new JetonsInvitation(SECRET + "-autre");
        autreSecret.init();
        assertThat(autreSecret.jeton(sel)).isNotEqualTo(jeton);
    }
}
