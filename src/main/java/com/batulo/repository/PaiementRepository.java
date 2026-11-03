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
     * Verrouille la ligne : la confirmation par l'utilisateur et le webhook Stripe peuvent arriver
     * en même temps, l'abonnement ne doit être prolongé qu'une fois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paiement p join fetch p.abonnement a join fetch a.entreprise "
            + "where p.stripeSessionId = :stripeSessionId")
    Optional<Paiement> verrouillerParStripeSessionId(String stripeSessionId);
}
