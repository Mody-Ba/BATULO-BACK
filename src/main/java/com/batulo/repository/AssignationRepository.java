package com.batulo.repository;

import com.batulo.entity.Assignation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignationRepository extends JpaRepository<Assignation, Long> {

    /** @param ligneeId id de la version 1 de la lignée */
    boolean existsBySondageIdAndGestionnaireId(Long ligneeId, Long gestionnaireId);

    Optional<Assignation> findBySondageIdAndGestionnaireId(Long ligneeId, Long gestionnaireId);

    @EntityGraph(attributePaths = {"gestionnaire", "assignePar"})
    List<Assignation> findBySondageIdOrderByDateAssignationAscIdAsc(Long ligneeId);

    /** Lignées assignées à un gestionnaire. */
    @Query("select a.sondage.id from Assignation a where a.gestionnaire.id = :gestionnaireId")
    List<Long> ligneesAssignees(Long gestionnaireId);

    /**
     * Gestionnaires assignés à plusieurs lignées en une requête : [ligneeId, gestionnaireId, prénom, nom, actif],
     * triés par prénom puis nom.
     */
    @Query("select a.sondage.id, g.id, g.prenom, g.nom, g.actif from Assignation a join a.gestionnaire g "
            + "where a.sondage.id in :ligneeIds order by g.prenom, g.nom, g.id")
    List<Object[]> gestionnairesDesLignees(Collection<Long> ligneeIds);
}
