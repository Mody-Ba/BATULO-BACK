package com.batulo.configs.security;

import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Règles d'accès liées au compte et à l'abonnement, appliquées à chaque requête authentifiée sur /api/** :
 * <ol>
 *   <li>Mot de passe temporaire pas encore changé (tous rôles) : seulement /api/auth/** (RG-06).</li>
 *   <li>ADMIN et GESTIONNAIRE, selon le statut effectif de l'abonnement de l'entreprise :
 *     <ul>
 *       <li>DESACTIVE : tout est bloqué (RG-01), y compris pour les gestionnaires ; seule la déconnexion reste ;</li>
 *       <li>EN_ATTENTE_PAIEMENT : seulement /api/auth/**, /api/abonnements/** (pour payer ou demander une
 *       augmentation) et /api/messagerie/** (écrire à Batulo) ;</li>
 *       <li>EXPIRE : lecture seule (GET), sauf /api/auth/**, /api/abonnements/**, /api/messagerie/** et l'envoi des
 *       statistiques d'un sondage terminé ;</li>
 *       <li>ACTIF ou EXPIRE_BIENTOT : accès complet.</li>
 *     </ul>
 *   </li>
 * </ol>
 * Le SUPER_ADMIN n'est pas concerné par la partie abonnement. En profil dev, /api/dev/paiements/** suit les mêmes
 * exceptions que /api/abonnements/** (il remplace le frontend de paiement).
 */
@Component
public class AccesAbonnementInterceptor implements HandlerInterceptor {

    private static final Pattern ENVOI_STATISTIQUES = Pattern.compile("^/api/sondages/\\d+/envois-statistiques$");

    private final ReponseErreurJson reponseErreurJson;

    public AccesAbonnementInterceptor(ReponseErreurJson reponseErreurJson) {
        this.reponseErreurJson = reponseErreurJson;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UtilisateurConnecte utilisateur)) {
            return true; // Route publique : rien à vérifier ici.
        }
        String chemin = request.getRequestURI().substring(request.getContextPath().length());
        if (chemin.startsWith("/api/public/")) {
            return true; // Routes du répondant : leurs règles (sondage ouvert, abonnement actif) sont dans le service.
        }
        boolean routeAuth = chemin.startsWith("/api/auth/");
        boolean routePaiement = chemin.startsWith("/api/abonnements/") || chemin.startsWith("/api/dev/paiements/");
        // Messagerie : l'admin doit pouvoir écrire à Batulo quand son abonnement n'est pas actif ; le service met la
        // conversation admin <-> gestionnaire en lecture seule dans ce cas.
        boolean routeMessagerie = chemin.startsWith("/api/messagerie/");
        // Envoi des statistiques d'un sondage terminé : du reporting, autorisé abonnement expiré.
        boolean routeEnvoiStatistiques = ENVOI_STATISTIQUES.matcher(chemin).matches();

        if (utilisateur.motDePasseTemporaire() && !routeAuth) {
            return refuser(response, "MOT_DE_PASSE_TEMPORAIRE",
                    "Vous utilisez un mot de passe temporaire : changez-le avec POST /api/auth/changer-mot-de-passe.");
        }
        if (utilisateur.role() == Role.SUPER_ADMIN || utilisateur.statutAbonnement() == null) {
            return true;
        }
        StatutAbonnement statut = utilisateur.statutAbonnement();
        return switch (statut) {
            case DESACTIVE -> chemin.equals("/api/auth/deconnexion") || refuser(response, "ABONNEMENT_DESACTIVE",
                    "Votre entreprise a été désactivée par Batulo : aucun accès n'est possible.");
            case EN_ATTENTE_PAIEMENT -> routeAuth || routePaiement || routeMessagerie || refuser(response,
                    "ABONNEMENT_EN_ATTENTE_PAIEMENT",
                    "Votre abonnement est en attente de paiement : payez une formule pour accéder à Batulo.");
            case EXPIRE -> lecture(request) || routeAuth || routePaiement || routeMessagerie || routeEnvoiStatistiques
                    || refuser(response, "ABONNEMENT_EXPIRE",
                    "Votre abonnement est expiré : accès en lecture seule. Renouvelez-le pour modifier vos données.");
            case ACTIF, EXPIRE_BIENTOT -> true;
        };
    }

    private static boolean lecture(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
    }

    private boolean refuser(HttpServletResponse response, String code, String message) throws IOException {
        reponseErreurJson.ecrire(response, HttpServletResponse.SC_FORBIDDEN, code, message);
        return false;
    }
}
