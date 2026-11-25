package com.batulo.repository;

import com.batulo.entity.InvitationEnvoi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface InvitationEnvoiRepository extends JpaRepository<InvitationEnvoi, Long> {

    /** Courriels envoyés (réussis ou non) par l'entreprise depuis cet instant : limite par heure glissante. */
    long countByEntrepriseIdAndDateEnvoiAfter(Long entrepriseId, Instant depuis);
}
