package com.batulo.repository;

import com.batulo.entity.EnvoiStatistique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnvoiStatistiqueRepository extends JpaRepository<EnvoiStatistique, Long> {

    @EntityGraph(attributePaths = "sondage")
    Page<EnvoiStatistique> findByAdminId(Long adminId, Pageable pageable);

    @EntityGraph(attributePaths = "sondage")
    Page<EnvoiStatistique> findByGestionnaireId(Long gestionnaireId, Pageable pageable);

    @EntityGraph(attributePaths = "sondage")
    Optional<EnvoiStatistique> findByIdAndAdminId(Long id, Long adminId);

    /** L'envoi, si ce compte en est l'admin destinataire ou le gestionnaire expéditeur. */
    @EntityGraph(attributePaths = "sondage")
    @Query("select e from EnvoiStatistique e where e.id = :id and (e.admin.id = :utilisateurId "
            + "or e.gestionnaire.id = :utilisateurId)")
    Optional<EnvoiStatistique> findPourParticipant(Long id, Long utilisateurId);
}
