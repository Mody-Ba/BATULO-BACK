package com.batulo.repository;

import com.batulo.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long>, JpaSpecificationExecutor<Invitation>,
        InvitationRepositoryCustom {

    Optional<Invitation> findByIdAndLigneeId(Long id, Long ligneeId);

    /** Lien personnel : le jeton est retrouvé par son SHA-256. */
    Optional<Invitation> findByJetonHash(String jetonHash);

    boolean existsByLigneeIdAndCourriel(Long ligneeId, String courriel);

    long countByLigneeId(Long ligneeId);

    /** Invitations envoyées au moins une fois avec succès, hors révoquées : base du taux de réponse. */
    long countByLigneeIdAndPremierEnvoiLeIsNotNullAndRevoqueeLeIsNull(Long ligneeId);

    @Query("select i.courriel from Invitation i where i.lignee.id = :ligneeId")
    List<String> courriels(Long ligneeId);

    List<Invitation> findByLigneeIdOrderByIdAsc(Long ligneeId);

    List<Invitation> findByLigneeIdAndIdIn(Long ligneeId, Collection<Long> ids);

    /** Envois en file pour l'entreprise : comptés dans la limite par heure avant d'être partis. */
    @Query("select count(i) from Invitation i where i.lignee.entreprise.id = :entrepriseId and i.envoiEnFileLe is not null")
    long compterEnFile(Long entrepriseId);

    /**
     * Réserve l'invitation pour une réponse, de façon atomique : 0 si elle a déjà répondu ou si elle est révoquée
     * (deux envois simultanés : un seul passe).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update Invitation i set i.reponduLe = :date where i.id = :id and i.reponduLe is null and i.revoqueeLe is null")
    int reserverReponse(Long id, Instant date);
}
