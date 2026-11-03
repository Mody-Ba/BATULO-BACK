package com.batulo.repository;

import com.batulo.entity.JetonReinitialisation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface JetonReinitialisationRepository extends JpaRepository<JetonReinitialisation, Long> {

    @EntityGraph(attributePaths = "utilisateur")
    Optional<JetonReinitialisation> findByJetonHash(String jetonHash);

    /** Invalide les liens encore valables d'un utilisateur : un seul lien actif à la fois. */
    @Modifying
    @Query("update JetonReinitialisation j set j.utiliseLe = :maintenant "
            + "where j.utilisateur.id = :utilisateurId and j.utiliseLe is null")
    int invaliderPourUtilisateur(Long utilisateurId, Instant maintenant);
}
