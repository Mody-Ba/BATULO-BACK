package com.batulo.repository;

import java.util.List;
import com.batulo.enums.TypePaiement;
import com.batulo.entity.Paiement;
import com.batulo.enums.StatutPaiement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    /**
     * Verrouille la ligne : deux confirmations simultanées du même paiement ne doivent prolonger
     * l'abonnement qu'une seule fois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paiement p join fetch p.abonnement a join fetch a.entreprise "
            + "where p.stripePaymentIntentId = :stripePaymentIntentId")
    Optional<Paiement> verrouillerParStripePaymentIntentId(String stripePaymentIntentId);

    @Query("select p from Paiement p join fetch p.abonnement a join fetch a.entreprise "
            + "where p.stripePaymentIntentId = :stripePaymentIntentId")
    Optional<Paiement> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Verrouille un paiement Moosyl par sa référence : webhook (et ses réessais) et relecture du statut ne prolongent
     * l'abonnement qu'une seule fois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paiement p join fetch p.abonnement a join fetch a.entreprise "
            + "where p.reference = :reference and p.fournisseur = com.batulo.enums.FournisseurPaiement.MOOSYL")
    Optional<Paiement> verrouillerParReference(String reference);

    /** Un paiement Moosyl de l'entreprise (404 pour toute autre entreprise). */
    @Query("select p from Paiement p join fetch p.abonnement a join fetch a.entreprise e "
            + "where p.reference = :reference and e.id = :entrepriseId "
            + "and p.fournisseur = com.batulo.enums.FournisseurPaiement.MOOSYL")
    Optional<Paiement> trouverMoosylDeLEntreprise(String reference, Long entrepriseId);

    /** Statut actuel en base (requête scalaire : jamais une entité déjà chargée dans la session). */
    @Query("select p.statut from Paiement p where p.reference = :reference")
    Optional<StatutPaiement> statutParReference(String reference);

    /** Achats de sondages (ou abonnements) d'une entreprise ayant un statut donné (tri porté par le Pageable). */
    Page<Paiement> findByAbonnementEntrepriseIdAndTypeAndStatut(Long entrepriseId, TypePaiement type,
                                                                StatutPaiement statut, Pageable pageable);

    /**
     * Revenus du tableau de bord du super admin, en une agrégation : par type et devise, montant encaissé, nombre de
     * paiements et sondages vendus (paiements REUSSI seulement).
     */
    @Query("select p.type, p.devise, sum(p.montant), count(p), sum(coalesce(p.quantite, 0)) from Paiement p "
            + "where p.statut = com.batulo.enums.StatutPaiement.REUSSI group by p.type, p.devise "
            + "order by p.type, p.devise")
    List<Object[]> revenus();

    /** Historique des paiements d'une entreprise ayant un statut donné (tri porté par le Pageable). */
    Page<Paiement> findByAbonnementEntrepriseIdAndStatut(Long entrepriseId, StatutPaiement statut, Pageable pageable);
}
