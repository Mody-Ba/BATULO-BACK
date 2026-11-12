package com.batulo.repository;

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

    /** Historique des paiements d'une entreprise ayant un statut donné (tri porté par le Pageable). */
    Page<Paiement> findByAbonnementEntrepriseIdAndStatut(Long entrepriseId, StatutPaiement statut, Pageable pageable);
}
