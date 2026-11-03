package com.batulo.repository;

import com.batulo.entity.JetonRafraichissement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JetonRafraichissementRepository extends JpaRepository<JetonRafraichissement, Long> {

    @EntityGraph(attributePaths = {"utilisateur", "utilisateur.entreprise"})
    Optional<JetonRafraichissement> findByJetonHash(String jetonHash);

    /** Révoque toutes les sessions d'un utilisateur (désactivation, nouveau mot de passe). */
    @Modifying
    @Query("update JetonRafraichissement j set j.revoque = true "
            + "where j.utilisateur.id = :utilisateurId and j.revoque = false")
    int revoquerTous(Long utilisateurId);
}
