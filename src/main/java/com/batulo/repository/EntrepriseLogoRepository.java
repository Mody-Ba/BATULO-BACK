package com.batulo.repository;

import com.batulo.entity.EntrepriseLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EntrepriseLogoRepository extends JpaRepository<EntrepriseLogo, Long> {

    /** Sans charger l'image. */
    @Query("select new com.batulo.repository.InfosLogo(l.entrepriseId, l.jeton, l.typeMime, l.taille, l.largeur, "
            + "l.hauteur, l.dateModification) from EntrepriseLogo l where l.entrepriseId = :entrepriseId")
    Optional<InfosLogo> findInfos(Long entrepriseId);

    Optional<EntrepriseLogo> findByJeton(String jeton);
}
