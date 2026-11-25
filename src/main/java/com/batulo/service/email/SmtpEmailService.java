package com.batulo.service.email;

import com.batulo.enums.Formule;
import com.batulo.enums.Langue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Envoi par SMTP via JavaMailSender (Mailpit en local : SMTP 1025, interface http://localhost:8025).
 * <ul>
 *   <li>Dans une transaction, le courriel part après sa validation : un compte annulé ne reçoit rien.</li>
 *   <li>Un échec d'envoi n'interrompt jamais l'opération : il est écrit dans les logs.</li>
 *   <li>Ni le contenu (mot de passe, lien) ni l'adresse du destinataire ne sont écrits dans les logs.</li>
 * </ul>
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private static final Pattern ADRESSE_COURRIEL = Pattern.compile("[^\\s<>\"]+@[^\\s<>\"]+");

    private static final String SIGNATURE = """

            --
            L'équipe Batulo
            Ce courriel est envoyé automatiquement, merci de ne pas y répondre.
            """;

    private final JavaMailSender mailSender;
    private final String expediteur;
    private final DateTimeFormatter formatDate;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${batulo.courriel.expediteur}") String expediteur,
                            @Value("${batulo.fuseau-horaire}") String fuseauHoraire) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
        this.formatDate = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH 'h' mm", Locale.CANADA_FRENCH)
                .withZone(ZoneId.of(fuseauHoraire));
    }

    @Override
    public void envoyerMotDePasseTemporaire(String courriel, String prenom, String motDePasse, Instant expireLe) {
        envoyer(courriel, "Batulo – Votre mot de passe temporaire", """
                Bonjour %s,

                Votre accès à Batulo est prêt.

                Identifiant : %s
                Mot de passe temporaire : %s

                Ce mot de passe est valable jusqu'au %s. À votre première connexion, remplacez-le par un mot de \
                passe personnel. Ne le communiquez à personne.
                """.formatted(prenom, courriel, motDePasse, date(expireLe)));
    }

    @Override
    public void envoyerConfirmationPaiement(String courriel, String prenom, Formule formule, BigDecimal montant,
                                            String devise, Instant dateExpiration, boolean renouvellement) {
        String sujet = renouvellement ? "Batulo – Abonnement renouvelé" : "Batulo – Abonnement activé";
        String action = renouvellement ? "Votre abonnement a bien été renouvelé." : "Votre abonnement est maintenant actif.";
        envoyer(courriel, sujet, """
                Bonjour %s,

                Nous avons bien reçu votre paiement. %s

                Formule : %s
                Montant : %s %s
                Valable jusqu'au : %s

                Merci de votre confiance.
                """.formatted(prenom, action, libelle(formule), montant.toPlainString(), devise, date(dateExpiration)));
    }

    @Override
    public void envoyerRappelExpiration(String courriel, String prenom, String entreprise, Instant dateExpiration,
                                        long joursRestants) {
        envoyer(courriel, "Batulo – Votre abonnement expire bientôt", """
                Bonjour %s,

                L'abonnement de %s expire dans %d jour(s), le %s.

                Renouvelez-le dès maintenant depuis votre espace pour garder l'accès complet : la nouvelle période \
                s'ajoutera à la date d'expiration actuelle, sans perte de jours.
                """.formatted(prenom, entreprise, joursRestants, date(dateExpiration)));
    }

    @Override
    public void envoyerAbonnementExpire(String courriel, String prenom, String entreprise, Instant dateExpiration) {
        envoyer(courriel, "Batulo – Votre abonnement est expiré", """
                Bonjour %s,

                L'abonnement de %s a expiré le %s.

                Votre espace est maintenant en lecture seule : vous et vos gestionnaires pouvez consulter vos \
                données, mais plus les modifier. Renouvelez votre abonnement pour retrouver l'accès complet.
                """.formatted(prenom, entreprise, date(dateExpiration)));
    }

    @Override
    public void envoyerAssignationSondage(String courriel, String prenom, String titreSondage, String nomAdmin) {
        envoyer(courriel, "Batulo – Un sondage vous a été assigné", """
                Bonjour %s,

                %s vous a assigné le sondage « %s ».

                Connectez-vous à Batulo pour le consulter, le prévisualiser et suivre sa collecte.
                """.formatted(prenom, nomAdmin, titreSondage));
    }

    @Override
    public void envoyerLienReinitialisation(String courriel, String prenom, String lien, Instant expireLe) {
        envoyer(courriel, "Batulo – Réinitialisation de votre mot de passe", """
                Bonjour %s,

                Une réinitialisation du mot de passe de votre compte Batulo a été demandée.
                Pour choisir un nouveau mot de passe, ouvrez ce lien :

                %s

                Ce lien est valable jusqu'au %s et ne peut servir qu'une fois.
                Si vous n'êtes pas à l'origine de cette demande, ignorez ce courriel : votre mot de passe ne change pas.
                """.formatted(prenom, lien, date(expireLe)));
    }

    @Override
    public boolean envoyerInvitation(String courriel, MessageInvitation m) {
        Locale locale = switch (m.langue()) {
            case FR -> Locale.FRENCH;
            case EN -> Locale.ENGLISH;
            case AR -> Locale.forLanguageTag("ar");
        };
        String fermeture = m.dateFermeture() == null ? null
                : DateTimeFormatter.ofPattern(m.langue() == Langue.AR ? "d MMMM yyyy، HH:mm" : "d MMMM yyyy, HH:mm",
                        locale).withZone(formatDate.getZone())
                .format(m.dateFermeture());
        String[] contenu = switch (m.langue()) {
            case FR -> new String[]{
                    (m.relance() ? "Rappel : " : "") + "votre avis sur « " + m.titreSondage() + " »",
                    (m.prenom() == null ? "Bonjour," : "Bonjour " + m.prenom() + ",") + "\n\n"
                            + (m.relance() ? "Nous n'avons pas encore reçu votre réponse. " : "")
                            + m.entreprise() + " vous invite à répondre au sondage « " + m.titreSondage() + " ».\n\n"
                            + "Votre lien personnel (ne le transférez pas, il ne sert qu'une fois) :\n\n" + m.lien()
                            + (fermeture == null ? "" : "\n\nLe sondage ferme le " + fermeture + ".")
                            + "\n\nMerci de votre participation.",
                    "\n\n--\nEnvoyé par Batulo pour " + m.entreprise()
                            + ".\nCe courriel est envoyé automatiquement, merci de ne pas y répondre.\n"};
            case EN -> new String[]{
                    (m.relance() ? "Reminder: " : "") + "your opinion on \"" + m.titreSondage() + "\"",
                    (m.prenom() == null ? "Hello," : "Hello " + m.prenom() + ",") + "\n\n"
                            + (m.relance() ? "We have not received your answers yet. " : "")
                            + m.entreprise() + " invites you to take the survey \"" + m.titreSondage() + "\".\n\n"
                            + "Your personal link (please do not share it, it can only be used once):\n\n" + m.lien()
                            + (fermeture == null ? "" : "\n\nThe survey closes on " + fermeture + ".")
                            + "\n\nThank you for taking part.",
                    "\n\n--\nSent by Batulo on behalf of " + m.entreprise()
                            + ".\nThis is an automated message, please do not reply.\n"};
            case AR -> new String[]{
                    (m.relance() ? "تذكير: " : "") + "رأيك في «" + m.titreSondage() + "»",
                    (m.prenom() == null ? "مرحبًا،" : "مرحبًا " + m.prenom() + "،") + "\n\n"
                            + (m.relance() ? "لم نتلقَّ إجاباتك بعد. " : "")
                            + "تدعوك " + m.entreprise() + " إلى المشاركة في الاستبيان «" + m.titreSondage() + "».\n\n"
                            + "رابطك الشخصي (لا تشاركه مع أحد، فهو صالح لاستخدام واحد فقط):\n\n" + m.lien()
                            + (fermeture == null ? "" : "\n\nيُغلق الاستبيان في " + fermeture + ".")
                            + "\n\nشكرًا لمشاركتك.",
                    "\n\n--\nأُرسلت هذه الرسالة عبر Batulo نيابةً عن " + m.entreprise()
                            + ".\nهذه رسالة آلية، يُرجى عدم الرد عليها.\n"};
        };
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(courriel);
        message.setSubject(contenu[0]);
        message.setText(contenu[1] + contenu[2]);
        String sujet = m.relance() ? "relance d'invitation" : "invitation";
        try {
            mailSender.send(message);
            log.info("Courriel « {} » ({}) envoyé", sujet, m.langue());
            return true;
        } catch (MailException e) {
            log.error("Échec de l'envoi du courriel « {} » : {} (cause : {}).", sujet, e.getClass().getSimpleName(),
                    causeRacine(e));
        } catch (RuntimeException e) {
            log.error("Échec inattendu de l'envoi du courriel « {} » : {}.", sujet, e.getClass().getSimpleName());
        }
        return false;
    }

    /** Envoie tout de suite, ou après la validation de la transaction en cours s'il y en a une. */
    private void envoyer(String destinataire, String sujet, String texte) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject(sujet);
        message.setText(texte + SIGNATURE);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envoyerSansEchec(message, sujet);
                }
            });
        } else {
            envoyerSansEchec(message, sujet);
        }
    }

    private void envoyerSansEchec(SimpleMailMessage message, String sujet) {
        try {
            mailSender.send(message);
            log.info("Courriel « {} » envoyé", sujet);
        } catch (MailException e) {
            log.error("Échec de l'envoi du courriel « {} » : {} (cause : {}). L'opération continue.",
                    sujet, e.getClass().getSimpleName(), causeRacine(e));
        } catch (RuntimeException e) {
            log.error("Échec inattendu de l'envoi du courriel « {} » : {}. L'opération continue.",
                    sujet, e.getClass().getSimpleName());
        }
    }

    /**
     * Message de l'exception racine (ex. « Connection refused »), adresses courriel masquées.
     */
    static String causeRacine(MailException e) {
        Throwable racine = e;
        // JavaMailSenderImpl range l'échec d'un message dans getMessageExceptions(), sans cause.
        if (e instanceof MailSendException envoi && envoi.getMessageExceptions().length > 0) {
            racine = envoi.getMessageExceptions()[0];
        }
        while (racine.getCause() != null && racine.getCause() != racine) {
            racine = racine.getCause();
        }
        String message = racine.getMessage();
        if (message == null || message.isBlank()) {
            return racine.getClass().getSimpleName();
        }
        return racine.getClass().getSimpleName() + ": " + ADRESSE_COURRIEL.matcher(message.strip()).replaceAll("***@***");
    }

    private String date(Instant instant) {
        return formatDate.format(instant);
    }

    private static String libelle(Formule formule) {
        return switch (formule) {
            case MENSUEL -> "mensuelle (1 mois)";
            case ANNUEL -> "annuelle (1 an)";
        };
    }
}
