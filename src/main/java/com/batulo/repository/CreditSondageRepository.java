package com.batulo.repository;

import com.batulo.entity.CreditSondage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditSondageRepository extends JpaRepository<CreditSondage, Long> {

    /** Ajoute un achat au solde en une requête (jamais lu puis réécrit : un débit simultané n'est pas perdu). */
    @Modifying(flushAutomatically = true)
    @Query("update CreditSondage c set c.solde = c.solde + :quantite, c.totalAchete = c.totalAchete + :quantite, "
            + "c.dateModification = :maintenant where c.entrepriseId = :entrepriseId")
    int crediter(Long entrepriseId, int quantite, Instant maintenant);

    /**
     * Utilise un crédit si le solde le permet, en une requête : deux publications simultanées avec un seul crédit
     * passent l'une après l'autre (verrou de ligne de PostgreSQL) et la seconde ne modifie rien.
     *
     * @return 1 si un crédit a été utilisé, 0 si le solde était à zéro (ou l'entreprise n'a jamais acheté)
     */
    @Modifying(flushAutomatically = true)
    @Query("update CreditSondage c set c.solde = c.solde - 1, c.totalUtilise = c.totalUtilise + 1, "
            + "c.dateModification = :maintenant where c.entrepriseId = :entrepriseId and c.solde > 0")
    int debiter(Long entrepriseId, Instant maintenant);

    /** Solde relu en base (requête scalaire : jamais une entité restée dans la session après un crédit ou débit). */
    @Query("select new com.batulo.repository.InfosCredits(c.solde, c.totalAchete, c.totalUtilise) "
            + "from CreditSondage c where c.entrepriseId = :entrepriseId")
    Optional<InfosCredits> infos(Long entrepriseId);

    /** Compte de crédits : l'entreprise a déjà acheté des sondages (quel que soit son solde). */
    boolean existsByEntrepriseIdAndTotalAcheteGreaterThan(Long entrepriseId, int minimum);

    /** Soldes de plusieurs entreprises en une requête (liste des admins du super admin). */
    List<CreditSondage> findByEntrepriseIdIn(Collection<Long> entrepriseIds);
}
