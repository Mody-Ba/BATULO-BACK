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
- Collecte (2.4) : `reponseUnique` par sondage (true par défaut) répond à la question ouverte du cahier. true :
  une réponse par appareil sur toute la lignée, contrôlée par un jeton UUID gardé par le navigateur (hash en base,
  409 DEJA_REPONDU) ; false : collecte terrain. Choisi dans le brouillon, figé à la publication, recopié dans les
  versions et les copies. Le lien public et le QR code sont stables par lignée (jeton porté par la version 1) et
  mènent à la version EN_COURS ; chaque réponse reste liée à sa version. Collecte fermée si l'entreprise est
  désactivée ou si l'abonnement n'est ni ACTIF ni EXPIRE_BIENTOT (410 SONDAGE_FERME). Matrice remplie : toutes les
  lignes. Limite de requêtes en mémoire par IP sur /api/public/** (une seule instance).

- Statistiques (3.1) : une clé de question stable (`question.cle`, UUID) est recopiée dans les versions et les
  copies, comme la valeur des options ; elle permet de cumuler une lignée (`?lignee=true`). En lignée, les libellés
  sont ceux de la dernière version qui contient la question ; une option ou une question retirée reste comptée
  pour les anciennes réponses ; un changement de type limite le calcul aux versions du type actuel. Pourcentages
  sur les répondants à la question (`sansReponse` à part) ; NUMERIQUE : une ligne par valeur jusqu'à 20 valeurs
  distinctes, sinon 10 tranches ; DATE par jour (écart de 62 jours ou moins) ou par mois, imposable avec
  `granulariteDate` ; textes paginés, sans analyse automatique. Calculs en SQL (agrégations), jamais en chargeant
  les réponses en mémoire. Route de dev (profil dev) : réponses aléatoires valides sur un sondage EN_COURS.
- Segmentation et tableaux croisés (3.2) : avancés en phase 3 (le cahier les place en phase 4). Une question
  CHOIX_UNIQUE, OUI_NON, NUMERIQUE ou DATE peut être « critère d'analyse » (`estCritere`, `critereConfig` :
  tranches NUMERIQUE [a ; b[, 5 tranches égales entre min et max par défaut ; DATE par MOIS ou ANNEE). Ce sont des
  réglages d'analyse : modifiables à tout moment (même après publication) par PUT .../questions/{id}/critere,
  recopiés dans les versions et les copies, jamais montrés au répondant. Filtres `critere=cle:valeur` : OU dans une
  même clé, ET entre clés. Tableau croisé : colonne « Non renseigné » si elle n'est pas vide ; `petitEffectif`
  sous `batulo.statistiques.effectif-minimum` (5). Un critère sensible (sexe) devrait être une question facultative
  (« volontairement renseigné ») : rappelé dans la documentation, non imposé.
- Exports (3.3) : réponses brutes en CSV ou XLSX (une ligne par réponse ; choix multiple et classement : une
  colonne par option, 1/0 ou rang, vide si non répondu ; matrice : une colonne par ligne ; LIBELLES ou CODES ;
  mêmes filtres que les statistiques). CSV UTF-8 avec BOM : VIRGULE (défaut, « ; » et virgule décimale) ou POINT
  (« , » et point décimal, Python/R) ; textes commençant par = + - @ précédés d'une apostrophe (injection de
  formule). Écriture en flux directement dans la réponse HTTP (curseur JDBC, SXSSF), sans StreamingResponseBody
  asynchrone. Rapports PDF (OpenPDF, police Liberation Sans embarquée, barres dessinées dans les tableaux) et XLSX
  (Résumé + une feuille par question), tableau croisé en PDF ou XLSX. Nom : titre-du-sondage_v2[_statistiques]_date.
  Le périmètre d'analyse (versions, références, filtres, critères) est partagé dans `AnalysePerimetre`.

## À traiter dans les étapes suivantes
- Phase 4 : invitations nominatives (l'unicité par appareil se contourne en vidant le navigateur ou en changeant
  d'appareil : limite acceptée pour la phase 2) ; diffusion courriel, SMS, WhatsApp, intégration web, campagnes.
- Si l'application tourne un jour sur plusieurs instances : limite de requêtes partagée (Redis ou Bucket4j).
- Phase 3 : messagerie et envoi des statistiques d'un sondage terminé du gestionnaire à son admin (3.4).
- Module 4.4, plus tard : logo et identité visuelle dans les rapports, comparaison de plusieurs sondages (la clé de
  question est déjà recopiée dans les copies), partage sécurisé des rapports, archivage.
