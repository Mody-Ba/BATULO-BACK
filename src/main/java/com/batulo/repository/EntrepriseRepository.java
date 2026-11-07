package com.batulo.repository;

import com.batulo.entity.Entreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

    /**
     * Supprime l'entreprise en SQL : les clés étrangères ON DELETE CASCADE suppriment aussi ses utilisateurs
     * (admin et gestionnaires), son abonnement, ses paiements, ainsi que les jetons et notifications des comptes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Entreprise e where e.id = :id")
    int supprimerAvecDependances(Long id);
}
