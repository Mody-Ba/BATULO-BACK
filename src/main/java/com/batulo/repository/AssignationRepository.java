package com.batulo.repository;

import com.batulo.entity.Assignation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
