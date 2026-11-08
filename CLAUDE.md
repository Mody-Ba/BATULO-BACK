# Batulo – backend

Plateforme SaaS de sondages (Spring Boot 3.5, Java 21, PostgreSQL 16, Liquibase).

## Références
- Cahier des charges : `docs/cahier-des-charges.pdf` — c'est la référence fonctionnelle. **En cas de différence
  avec ce fichier, CLAUDE.md prime** (voir « Décisions qui s'écartent du cahier »).
- Architecture à reproduire : projet ProxyMed (`/Users/proxymed_auth`, lecture seule) — `configs/` (+ `security/`),
  `controller/`, `entity/`, `enums/`, `exception/`, `mappers/` (API : Request -> Model, Model -> Response),
  `repository/`, `service/` (interface + `Impl`, `service/mappers/` : Entity -> Model, `service/model/` : records).
  Les entités ne sortent jamais des services. Pas de Lombok.

## Méthode de travail
- Une étape à la fois : plan d'abord, accord de l'utilisateur, puis code ; attendre sa validation avant l'étape suivante.
- Aucune commande Git : l'utilisateur gère le dépôt.
- L'application de l'utilisateur tourne sur le port 8090 (IntelliJ) : ne jamais l'arrêter. Tester sur 8092 avec une
  base à part (`batulo_test`) et arrêter son instance à la fin. Ne jamais écrire dans la base `batulo` ni dans le
  Mailpit de l'utilisateur.
- Tests : `./mvnw test`. Phase 1 sur H2 (profil `test`) ; phase 2 sur PostgreSQL 16 via Testcontainers
  (`PostgresIntegrationTestBase`).
- Erreurs de l'API en JSON `{code, message}`.
- Un changeset Liquibase déjà livré ne se modifie jamais : on en ajoute un nouveau.

## Décisions qui s'écartent du cahier (elles priment)
- Architecture en couches de ProxyMed, et non le découpage par package fonctionnel (auth, sondage...) du cahier §6.
- L'entité des options s'appelle `OptionQuestion` (le cahier dit `OptionReponse`).
- Versions et modèles de formulaires sont faits dès la phase 2 (le cahier les place en phase 4).
- Statut d'un sondage calculé à partir des dates (NON_COMMENCE, EN_COURS, TERMINE), sans tâche planifiée pour la
  publication programmée ; le champ `publie` distingue le brouillon du sondage planifié.
- Seul un brouillon est modifiable ; un sondage publié est gelé (409 SONDAGE_PUBLIE) et se modifie par une nouvelle
  version (dernière version publiée seulement : VERSION_OBSOLETE ; une seule en préparation : BROUILLON_EXISTANT).
  Publier la version N ferme les précédentes au moment où elle s'ouvre ; chaque réponse reste liée à sa version.
- Suppression : brouillon seulement ; un sondage publié se ferme mais ne se supprime pas (le cahier, GE-03, dit
  « supprime ses propres sondages »).
- Réutilisation : un sondage se crée vide ou à partir de n'importe quel sondage visible de l'entreprise (même
  TERMINE) ; copie du contenu avec les mêmes valeurs d'options, sans dates, publication, assignations ni réponses.
  `sondageOrigineId` = sondage dont il est issu (source réutilisée/dupliquée, ou version précédente) ; les versions
  sont reliées par `lignee_id`. `estModele` marque un modèle.
- Valeur d'une option : générée depuis le libellé si absente, unique dans la question, jamais régénérée.
- Assignation (2.3) : elle porte sur la lignée (table `assignation`, version 1), donc toutes les versions partagent
  les mêmes assignations ; une copie (réutilisation, duplication) n'en hérite pas. Un gestionnaire voit une lignée
  s'il a créé l'une de ses versions ou si elle lui est assignée. Il ne gère (modifie, publie, ferme, versionne,
  supprime, marque modèle) que les versions qu'il a créées, avec la permission : sinon 403 ACTION_RESERVEE_CREATEUR.
  Le gestionnaire assigné voit, prévisualise, suivra la collecte et verra les statistiques (« ses sondages » du
  cahier = créés ou assignés). Assigner un gestionnaire désactivé : 409 ; ses assignations sont conservées.
- Notifications ajoutées au cahier : assignation d'un sondage (tableau de bord + courriel) et retrait d'une
  assignation (tableau de bord seulement).

## À traiter dans les étapes suivantes
- 2.4 : un abonnement EXPIRE ou DESACTIVE ferme les sondages aux répondants (cahier §5) ; trancher la question
  ouverte du cahier « un répondant peut-il répondre plusieurs fois au même sondage ? » ; interdire la suppression
  d'un sondage qui a des réponses.
- Phase 3 : le gestionnaire envoie les statistiques d'un sondage terminé à son admin.
