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
- Messagerie (3.4) : conversations à deux, toujours ancrées sur un admin (`conversation.admin_id`) : avec l'un de
  ses gestionnaires, ou (`gestionnaire_id` null) avec Batulo, boîte partagée par tous les super admins. Un
  gestionnaire ne parle qu'à son admin ; jamais entre entreprises ; conversation inaccessible : 404. Texte brut
  (5000 car.), sans pièce jointe ni suppression ; lu/non lu par côté (`message.cote`). Notification « Nouveau
  message » (tableau de bord, pas de courriel) au premier message non lu seulement. Abonnement EXPIRE ou
  EN_ATTENTE_PAIEMENT : /api/messagerie/** reste ouvert, mais la conversation admin <-> gestionnaire est en
  lecture seule (403 ABONNEMENT_EXPIRE) ; celle avec Batulo reste ouverte.
- Envoi des statistiques (3.4, GE-06) : réservé au gestionnaire créateur ou assigné, sondage TERMINE (en lignée :
  toutes les versions publiées), sinon 409 SONDAGE_NON_TERMINE ; autorisé abonnement expiré. Filtres enregistrés
  (`envoi_statistique.filtres`), statistiques recalculées à l'ouverture (pas de copie figée) ; nom de l'expéditeur
  conservé si le gestionnaire est supprimé. Message automatique (type STATISTIQUES) et notification
  STATISTIQUES_RECUES ; l'admin a /api/statistiques-recues (+ PDF de 3.3).
- Demande d'augmentation (3.4, AD-05, SA-07) : purement informative (RG-09, aucun effet sur l'abonnement : acceptée,
  l'admin paie lui-même) ; une seule EN_ATTENTE par entreprise (409 DEMANDE_EN_COURS) ; postée dans la conversation
  avec Batulo ; le super admin répond TRAITEE ou REFUSEE une seule fois (409 DEMANDE_DEJA_TRAITEE).
- Ajouts pour le frontend : une lignée est représentée (GET /api/sondages?derniereVersion=true, tableau de bord) par
  sa dernière version démarrée (EN_COURS ou TERMINE), sinon par sa dernière version ; `brouillonEnPreparation(Id)`
  signale un brouillon plus récent que la ligne (une version planifiée plus récente n'est pas signalée). La liste
  donne `nombreReponses` (version), `nombreReponsesLignee` et `gestionnairesAssignes` (null pour un gestionnaire,
  comme GET .../assignations), calculés par requêtes groupées (`ResumesSondages`, pas de N+1). Recherche des
  gestionnaires sans majuscules ni accents par `lower` + `translate` (pas d'extension unaccent), jokers échappés.
  GET /api/abonnements/paiements : paiements REUSSI seulement, du plus récent au plus ancien.
- Ajouts pour l'espace super admin : GET /api/super-admin/tableau-de-bord (entreprises par statut d'abonnement
  affiché, total des entreprises, demandes d'augmentation EN_ATTENTE ; une agrégation SQL `sum(case ...)`).
  GET /api/super-admin/admins renvoie des lignes enrichies (entreprise, admin, abonnement avec statut affiché et
  jours restants, nombre de gestionnaires ; requêtes groupées, pas de N+1), filtres `statut` et `recherche` (nom de
  l'entreprise, prénom, nom, courriel ; `RechercheTexte`, partagé avec les gestionnaires) combinés par ET. Le statut
  affiché se traduit en SQL par une seule fonction, `AbonnementSpecifications.condition`, utilisée partout. Refuser
  une demande d'augmentation exige un message (400 REPONSE_OBLIGATOIRE) ; l'accepter, non.
- Ajouts pour l'éditeur : GET .../versions donne `nombreReponses` par version (une requête groupée). PUT
  /api/sondages/{id}/questions/{questionId}/section `{sectionId, position?}` déplace une question vers une section du
  même sondage (ou la repositionne dans la sienne) : brouillon seulement, mêmes droits que la modification d'une
  question ; position à partir de 1, à la fin si absente, 400 POSITION_INVALIDE hors limites, LIMITE_ATTEINTE si la
  section est pleine ; la question garde son id, sa clé, ses options et son critère, les deux sections sont
  renumérotées. Avec `orphanRemoval`, une question ne se retire jamais de la collection de sa section pour la
  déplacer (Hibernate la supprimerait) : on change `question.section` et les ordres, puis on relit le sondage.
- Ajouts pour les statistiques : CORS expose `Content-Disposition` et accepte l'en-tête `X-Jeton-Repondant`.
  GET /api/sondages/{id}/reponses : `?lignee=true` (toutes les versions) ou `?version=N` (version N de la lignée,
  404 VERSION_INTROUVABLE), jamais les deux (400 PARAMETRE_INVALIDE) ; chaque réponse donne `sondageId` et `version`
  (version chargée dans la même requête). Le formulaire public a ses propres enregistrements (`*PubliqueResponse`),
  sans réglage d'analyse ni clé. Sondage à réponse unique : `dejaRepondu` d'après l'en-tête facultatif
  `X-Jeton-Repondant` (jamais dans l'URL), sur toute la lignée ; jeton absent ou mal formé : false (le POST garde
  son 400) ; absent du JSON si `reponseUnique` est faux ; le formulaire est renvoyé dans tous les cas.
- Ajouts pour la messagerie : GET /api/notifications `?lue=true|false` (totaux filtrés). Chaque notification porte
  `referenceId` (sans clé étrangère) et `conversationId` (SET NULL), null pour les anciennes : inscription, paiement,
  expiration -> abonnement ; assignation et retrait -> lignée (version 1) ; nouveau message -> message +
  conversation ; statistiques reçues -> envoi + conversation ; demandes -> demande + conversation avec Batulo.
  `notifier`/`notifierSuperAdmins` exigent ces deux valeurs : toute nouvelle notification doit les renseigner.
  GET /api/super-admin/demandes-abonnement/{id} ; GET /api/envois-statistiques/{id} pour l'admin destinataire et le
  gestionnaire expéditeur seulement (404 ENVOI_INTROUVABLE sinon), sans marquer l'envoi consulté. OpenAPI : deux
  records imbriqués de même nom simple partagent un schéma : leur donner `@Schema(name = ...)` (CelluleCroisement,
  AbonnementLigneAdmin), le JSON ne change pas.
- Page d'accueil d'un sondage : `objetEnquete`, `texteConfidentialite`, `consentementRequis` (false par défaut),
  `texteConsentement` (obligatoire si consentement exigé : 400 TEXTE_CONSENTEMENT_REQUIS). Modifiés par PUT
  /api/sondages/{id} dans le brouillon seulement : champ absent = inchangé, "" efface. Recopiés comme `reponseUnique`
  (versions, duplications, réutilisations) ; renvoyés par le sondage, l'aperçu et le formulaire public. Si la version
  EN_COURS exige le consentement, chaque envoi doit porter `consentement: true` (400 CONSENTEMENT_REQUIS, contrôlé
  avant DEJA_REPONDU) ; `reponse.date_consentement` = heure du serveur, null sinon. `dateConsentement` dans le détail
  des réponses ; colonne « Consentement » après « Version » dans les exports CSV/XLSX seulement si l'une des versions
  exportées l'exige (vide pour les réponses d'une version qui ne l'exigeait pas).
- Paiement mobile Moosyl (https://docs.moosyl.com, `https://api.moosyl.com`, même URL en sandbox : l'environnement
  dépend de la clé secrète, envoyée telle quelle dans `Authorization`, jamais journalisée). Prix en MRU entiers
  (`batulo.moosyl.prix-*-mru`, 40/80). `paiement.fournisseur` (STRIPE/MOOSYL), `reference` (UUID = transactionId
  Moosyl), `session_externe_id`. POST /api/abonnements/paiement-mobile crée le paiement EN_ATTENTE et la session
  (retour `MOOSYL_URL_RETOUR?reference=`). Le webhook POST /api/webhooks/moosyl (public, hors /api/public/** et de sa
  limite par IP ; signature `x-webhook-signature` = sha256= HMAC-SHA256 du corps brut, 401 sinon) est la source
  principale : `completed` active (montant vérifié), `failed`/`cancelled` -> ECHOUE (un nouvel essai réussi active
  encore), le reste -> 200 sans effet. GET .../paiement-mobile/{reference} relit la session
  (`GET /checkout-session/public/{id}`, absente de la documentation mais vérifiée sur le sandbox : sans clé,
  `data.status` open/completed/expired/cancelled ; échec = on attend le webhook) tant
  qu'il est EN_ATTENTE, hors transaction. Toute activation (Stripe compris) passe par `ActivationPaiement`, sous
  verrou du paiement : un paiement déjà REUSSI ne prolonge jamais deux fois. Route de dev POST
  /api/dev/paiements/moosyl/webhook : webhook signé envoyé en HTTP au backend local (tests sans tunnel).
- Questions conditionnelles (phase 4, étape 1) : une question ou une section a une condition d'affichage facultative
  (`condition_affichage`, JSONB : `{combinaison ET|OU, regles: [{question: cle, operateur, valeur}]}`) ; section
  masquée = toutes ses questions masquées. L'API désigne la source par `questionId` (requête et réponses ; le
  formulaire public n'expose jamais la clé), la base par sa `cle` : les copies (versions, duplications,
  réutilisations) recopient le JSON tel quel et les références suivent. Opérateurs par type de source
  (`OperateurCondition.permis`) : CHOIX_UNIQUE EGAL/DIFFERENT/PARMI ; CHOIX_MULTIPLE CONTIENT/PARMI (au moins une) ;
  OUI_NON EGAL/DIFFERENT ; NUMERIQUE, ECHELLE, DATE EGAL/DIFFERENT/SUPERIEUR/INFERIEUR (stricts)/ENTRE [min, max]
  (bornes comprises) ; tous REPONDUE/NON_REPONDUE. Valeurs d'options par leur valeur, jamais le libellé. Source sans
  réponse ou masquée : seule NON_REPONDUE est vraie (DIFFERENT compris). Routes PUT/DELETE
  .../questions/{id}/condition et .../sections/{id}/condition, brouillon seulement (gelées à la publication) ; 1 à 10
  règles. Source placée AVANT (section précédente, ou plus haut dans la même section ; section : section précédente),
  ce qui exclut les cycles. `ValidateurConditions` : 400 CONDITION_INVALIDE en posant la condition ; 409
  CONDITION_DEPENDANTE (refus, jamais de retrait automatique) si une suppression (question, section), une
  réorganisation, un déplacement, un changement de type ou le retrait d'une option casserait une condition ;
  revérifié à la publication. `EvaluateurConditions` (classe pure, seul évaluateur) parcourt le formulaire dans
  l'ordre : à l'envoi, `ValidateurReponse` recalcule la visibilité (jamais confiance au front), n'exige une question
  obligatoire que visible, ignore (sans la valider) la réponse d'une question masquée ; la route de dev respecte les
  conditions. Pas de brouillon ni de reprise de réponse aujourd'hui : l'évaluateur est prévu pour. Les questions
  masquées d'une réponse vont dans `reponse_question_masquee` : statistiques `vues` = réponses aux versions de la
  question - masquées (SQL), `sansReponse` = vues - répondants, `conditionnelle` ; « Vue par » dans les rapports PDF
  et XLSX ; cellule vide dans les exports bruts. Anciennes réponses : aucune ligne masquée, donc toutes « vues ».
- Personnalisation de l'entreprise (phase 4, étape 2) : `entreprise.nom_affiche` et `couleur_principale` (#RRGGBB
  en majuscules), logo dans la table `entreprise_logo` (une ligne par entreprise, BYTEA en base pour Cloud Run, jamais
  chargé avec l'entreprise). Lecture GET /api/entreprise/personnalisation (admin et gestionnaires) ; écriture ADMIN
  seulement : PUT /api/admin/personnalisation (absent = inchangé, "" efface ; 400 COULEUR_INVALIDE), PUT
  /api/admin/personnalisation/logo (multipart, champ `fichier`), DELETE .../logo. Aucune route ne prend d'id
  d'entreprise. Logo : PNG ou JPEG seulement (WebP plus tard : le JDK ne le lit pas, pas de dépendance ajoutée), type
  reconnu aux premiers octets (extension et Content-Type ignorés : 400 LOGO_FORMAT_INVALIDE), dimensions lues dans
  l'en-tête avant décodage (2000 x 2000 max : LOGO_DIMENSIONS), image décodée une fois (LOGO_ILLISIBLE), 1 Mo
  (`spring.servlet.multipart` et `batulo.personnalisation.logo-taille-max` : 413 FICHIER_TROP_GROS). Route publique
  GET /api/public/logos/{jeton} (jeton UUID régénéré à chaque remplacement : ancienne URL 404 ; Cache-Control un an
  immutable, nosniff, CSP default-src 'none' ; reste sous la limite par IP). Couleur par défaut
  `batulo.personnalisation.couleur-defaut` = #005CBB (primaire du thème du front, azure ton 40) ; `couleurTexte`
  #FFFFFF ou #000000 selon le meilleur contraste WCAG (`ContrasteCouleur`). Le formulaire public et l'aperçu ont
  `apparence` {nomAffiche, logoUrl absolue, couleurPrincipale, couleurTexte} et rien d'autre sur l'entreprise.
  Rapports PDF : logo et nom affiché sur la page de titre et dans l'en-tête des pages (« Batulo » sans nom affiché ni
  logo) ; « Entreprise » = nom affiché. Fiche super admin (GET /api/super-admin/admins/{id}) : `personnalisation`.
- Langues des sondages (phase 4, étape 3, partie 2/3) : `sondage.langue_principale` (fr, en, ar ; fr par défaut,
  énumération `Langue`, JSON et base en code minuscule) et `langues_supplementaires` (JSONB, sans la principale, 2 au
  plus), modifiables en brouillon par PUT /api/sondages/{id}/langues (400 LANGUE_INVALIDE : doublon, principale
  répétée, code inconnu). Changer la langue principale ne change que l'étiquette (rien n'est traduit ni échangé) ; les
  traductions d'une langue retirée sont gardées mais ignorées. Traductions en JSONB `traductions` sur sondage (titre,
  description, objetEnquete, texteConfidentialite, texteConsentement), section (titre, description), question
  (libelle, aide, libelleMin, libelleMax, `lignes`/`colonnes` de matrice rangées sous leur texte principal) et option
  (libelle) : `{"en": {...}}`. Valeurs stables (clés, valeurs d'options, textes principaux des lignes et colonnes de
  matrice) jamais traduites. `TextesTraduisibles` : inventaire dans l'ordre du formulaire (textes principaux non vides,
  consentement seulement s'il est exigé), clés d'API `sondage.titre`, `section.{id}.titre`, `question.{id}.libelle`,
  `question.{id}.ligne.{n}`, `option.{id}.libelle`... `Traducteur` (classe pure) : seul résolveur, traduction sinon texte
  principal ; langue non proposée par la version = langue principale. Éditeur (`TraductionController`) : GET
  .../traductions (complétude par langue : traduits, total, manquants), GET .../traductions/{langue} (côte à côte : source
  et traduction), PUT .../traductions/{langue} (en une fois : absent = inchangé, null ou "" efface ; 400
  TRADUCTION_INVALIDE si clé inconnue ou en double, trop longue ; tout est contrôlé avant d'écrire). Lecture : comme le
  sondage ; écriture : brouillon (409 SONDAGE_PUBLIE), droits de modification. Un texte principal modifié (sondage,
  section, question, option, ligne de matrice renommée) perd ses traductions. Publication : 400 TRADUCTIONS_INCOMPLETES
  avec `manquants` [{langue, cle, source}] (champ ajouté à `ErrorResponse`). Copies (versions, duplications,
  réutilisations) : langues et traductions recopiées ; un titre ou une description remplacés à la copie perdent leur
  traduction (« Copie de ... »). Formulaire public et aperçu : `?langue=` (non proposée ou inconnue : langue
  principale), réponse avec `langue` (servie), `languePrincipale`, `languesDisponibles` ; ECHELLE : libelleMin/Max
  traduits dans la configuration ; MATRICE : `configuration.lignes/colonnes` restent les valeurs à envoyer et
  `libellesMatrice` {lignes, colonnes} donne les libellés au même rang. Envoi : `langue` facultative (principale par
  défaut), 400 LANGUE_INVALIDE si la version EN_COURS ne la propose pas (contrôlée avant DEJA_REPONDU) ;
  `reponse.langue` (anciennes réponses : fr), renvoyée dans le détail des réponses. Conditions, validation, réponse
  unique : indépendantes de la langue. Analyse (`LanguesAnalyse` dans `AnalysePerimetre`) : `?langue=` pour les libellés
  (langue principale du sondage demandé par défaut ; en lignée, chaque version la sert si elle la propose) et
  `?langueReponse=` répétable (OU, filtre SQL `r.langue`) sur statistiques, critères, croisement, textes, exports et
  rapports ; statistiques : `langue`, `languesReponse`, `repartitionLangues` ; matrice : `libellesColonnes` et
  `LigneMatrice.libelle` à côté des textes principaux. Export brut : colonne « Langue » (code) après Version et
  Consentement ; en LIBELLES, en-têtes, options et colonnes de matrice traduits ; en CODES, valeurs stables. Envoi des
  statistiques : `langueReponse` enregistré dans les filtres. Route de dev : langue au hasard parmi celles du sondage.
  Rapports PDF : Noto Sans Arabic (OFL, `resources/polices/noto/` avec OFL.txt) choisie caractère par caractère
  (`FontSelector`) avec Liberation Sans ; un texte contenant de l'arabe est écrit de droite à gauche dans une cellule
  (OpenPDF ne façonne l'arabe que dans les cellules et colonnes, pas dans un paragraphe) ; façonnage vérifié sur image
  (lettres liées, lam-alif) ; diacritiques placés approximativement. Textes fixes des rapports et des exports (« Oui »,
  « Résumé », « Langue »...) toujours en français.
- Invitations nominatives (phase 4, étape 4) : en plus du lien public (inchangé). Table `invitation` sur la lignée
  (version 1) : toutes les versions partagent la liste (une nouvelle version ne copie rien), une seule réponse par
  invité sur la lignée ; duplication et réutilisation : aucune invitation, la liste se reprend par POST
  .../invitations/reprendre {sondageSourceId} (identité seulement, sans statut ni lien). Courriel en minuscules, unique
  par lignée (409 INVITE_EXISTANT) ; 10 000 invités au plus. Routes /api/sondages/{id}/invitations/** ({id} = n'importe
  quelle version) : liste paginée (statut, recherche `RechercheTexte`), /resume (une agrégation `sum(case ...)` ; taux =
  répondus / envoyées hors révoquées), ajout, modification et suppression avant réponse (409 INVITE_A_REPONDU), /revoquer
  (définitif), /import (CSV multipart `fichier`, UTF-8 avec ou sans BOM, « , » ou « ; » détecté sur l'en-tête, en-têtes
  prenom, nom, courriel, telephone, langue sans majuscules ni accents, 1000 lignes max : 400 IMPORT_TROP_GRAND ; rapport
  acceptées / refusées avec raison), /envoyer et /relancer (202), /export (CSV du suivi). Accès : `sondageLisible`
  (admin, gestionnaire créateur ou assigné ; sinon 404). Statut calculé (`RegleInvitation`, SQL dans
  `InvitationSpecifications.condition`) : REVOQUEE > A_REPONDU > OUVERTE > EN_ECHEC > INVITEE > A_INVITER.
  Jeton personnel = base64url(HMAC-SHA256(INVITATION_SECRET, sel)) : seuls le sel et le SHA-256 du jeton sont en base,
  le même lien est recalculé pour chaque relance (`JetonsInvitation`). INVITATION_SECRET est dédié (jamais JWT_SECRET),
  32 octets minimum, sinon l'application refuse de démarrer ; le changer invalide les liens envoyés. Lien
  {PUBLIC_FORM_URL}/{jetonSondage}?i={jetonInvitation} ; l'API ne le reçoit jamais dans l'URL : en-tête
  `X-Jeton-Invitation` (formulaire) et `jetonInvitation` (envoi). Inconnu ou d'une autre lignée : 404
  INVITATION_INVALIDE ; révoqué : 410 INVITATION_REVOQUEE ; déjà répondu : 409 DEJA_REPONDU (réservation atomique
  `update ... where repondu_le is null and revoquee_le is null`), le jeton d'appareil n'est alors pas utilisé.
  Formulaire avec lien : langue préférée de l'invité si `?langue=` est absent, `ouverte_le` à la première ouverture,
  `invitation {dejaRepondu, reponsesNominatives}`, jamais d'identité. Anonymat par défaut : `reponse.invitation_id`
  jamais rempli, `repondu_le` arrondi au jour (minuit, fuseau de Batulo) pour ne pas recouper `date_soumission`.
  `sondage.reponses_nominatives` (brouillon, recopié comme `reponseUnique`) : `reponse.invitation_id` et `repondu_le`
  exact ; `invite` {prénom, nom, courriel} dans le détail des réponses, colonnes « Invité » et « Courriel de l'invité »
  après « Langue » dans les exports si une version exportée est nominative. Envoi : `EmailService.envoyerInvitation`
  (synchrone, renvoie succès ou échec, fr/en/ar selon la langue de l'invité, titre traduit, nom affiché, lien, date de
  fermeture) ; seulement si une version est EN_COURS ou planifiée (409 SONDAGE_NON_OUVERT) et si l'entreprise peut
  collecter (409 SONDAGE_FERME). La requête réserve (`envoi_en_file_le`, entreprise verrouillée) puis `EnvoiInvitations`
  envoie après validation dans l'exécuteur `envoiInvitationsExecutor` (2 fils, file en mémoire, une seule instance ;
  `batulo.invitations.envoi-synchrone=true` en test), une transaction courte avant et après chaque courriel SMTP.
  Envoyer : jamais invités (un premier envoi en échec se retente) ; relancer : déjà invités, sans réponse, dernier envoi
  réussi il y a 24 h ou plus (DELAI_RELANCE). Limite `batulo.invitations.envois-par-heure` (500) par entreprise,
  comptée dans `invitation_envoi` (journal qui survit à la suppression d'un invité) plus les envois en file : lot
  refusé en entier, 429 LIMITE_ENVOIS (`LimiteDepasseeException`).

## À traiter dans les étapes suivantes
- Phase 4 : brouillon et reprise d'une réponse (réutiliser `EvaluateurConditions.Parcours`), pour le lien public comme
  pour le lien personnel (le brouillon d'un invité se rattachera à son invitation) ; diffusion SMS (le téléphone des
  invités est déjà saisi), WhatsApp, intégration web, campagnes. L'unicité par appareil du lien public se contourne en
  vidant le navigateur ou en changeant d'appareil (limite acceptée) ; les invitations nominatives n'ont pas ce défaut.
- Si l'application tourne un jour sur plusieurs instances : limite de requêtes partagée (Redis ou Bucket4j).
- Phase 4, étape 3 (partie 3/3) : textes fixes des rapports et exports dans la langue demandée (« Oui », « Résumé »,
  en-têtes), mise en page de droite à gauche du rapport PDF en arabe.
- Module 4.4, plus tard : logo dans les rapports XLSX (PDF fait), logo WebP, comparaison de plusieurs sondages (la clé de
  question est déjà recopiée dans les copies), partage sécurisé des rapports, archivage.
