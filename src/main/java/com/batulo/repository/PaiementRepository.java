package com.batulo.repository;

import com.batulo.entity.Paiement;
import jakarta.persistence.LockModeType;
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
}
