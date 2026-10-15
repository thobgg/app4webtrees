# Fiche individuelle et modification

La fiche individuelle s'ouvre par un **double-clic** sur une personne, avec **Ctrl+E** ou par l'icône **Modifier**. Elle montre tout ce qui concerne une personne et sert aussi à la modifier.

## Présentation

![](desktop-personenblatt.png)


Nom, dates et image en haut. En dessous, les onglets :

- **Données :** tous les événements sous forme de table (événement, date, lieu / description), plus l'âge au décès.
- **Biographie :** la chronologie avec le mariage, les naissances des enfants et l'âge à chaque événement. Elle liste aussi les événements où la personne était parrain, marraine ou témoin (« Marraine à : Baptême de … ») ; un clic mène à l'enfant ou au couple.
- **Parrains, marraines et témoins :** sous le baptême et le mariage apparaît une ligne « Parrains et marraines : » ou « Témoins : » – dans la chronologie, dans la zone de détail de la table des données, dans la vue famille et sur les fiches. Les parrains et marraines ayant leur propre enregistrement sont soulignés et cliquables, les autres (issus d'une note « Paten: … » ou du champ GEDCOM-L _GODP) apparaissent en texte. ⓘ déplie une note sur le parrain ou la marraine, l'icône de source ouvre la source. Les parrains et marraines vivants que vous n'êtes pas autorisé à voir apparaissent seulement comme « Privé ». Sous la table des données, la section **Rôles de parrain, marraine et témoin** liste tous les baptêmes et mariages où la personne était parrain, marraine ou témoin ; un clic sur le titre la replie. Les deux nécessitent api4webtrees 1.11 ou plus récent ; avec un module plus ancien, la note sur les parrains reste simplement une note.
- **Type de mariage :** le mariage civil et le mariage religieux apparaissent comme des événements distincts avec leur type ; les listes et la vérification utilisent le mariage civil lorsque les deux existent.
- **Parents/fratrie** : parents et fratrie, y compris demi-frères et demi-sœurs. Un clic passe à cette personne.
- **Conjoints/enfants** : les unions à gauche, les enfants de l'union sélectionnée à droite, en dessous ses événements (mariage, divorce, résidence …) à ajouter, modifier et supprimer. Un double-clic sur un conjoint ou un enfant affiche sa fiche ; **+** ajoute un conjoint ou un enfant de cette union.
- **Nom** : prénoms, nom de famille et suffixe du nom ont chacun leur champ lors de la modification.
- **Simple / Complet** (en bas de la fiche) : « Simple » affiche dans l'onglet Données un formulaire avec nom, naissance, baptême, religion, profession, mariage par union, décès et inhumation à remplir directement ; « Complet » affiche la table de tous les événements avec l'âge et des marques pour note et source (cliquez sur un en-tête de colonne pour trier).
  Seul ce que vous avez modifié est enregistré ; les sources, notes et autres détails de l'événement sont conservés. Si un événement existe plusieurs fois, le formulaire modifie le premier. Une date que le programme ne sait pas interpréter (« printemps 1850 ») est enregistrée comme texte de date. L'enregistrement a lieu quand vous quittez un champ (Tab ou clic ailleurs), comme partout dans le programme. Une date non valide reste en rouge et n'est pas envoyée tant que vous ne l'avez pas corrigée ou choisi « Enregistrer quand même en texte » ; ce n'est qu'alors que la fermeture ou la sortie du programme demande d'abord confirmation.
- **Notes**, **Sources**, **Médias**.
- **Carte :** les lieux de vie sous forme de liste avec des liens vers OpenStreetMap.

## Feuilleter

**Page Up** et **Page Down** passent à la personne précédente ou suivante de la liste, **Ctrl+Home** et **Ctrl+End** à la première et à la dernière. **Esc** ferme la fenêtre.

## Modifier

![](desktop-paten-dialog.jpg)


La modification nécessite les droits de modification dans webtrees. Les changements partent immédiatement vers webtrees ; selon les réglages de l'arbre, ils s'appliquent tout de suite ou attendent l'approbation d'un modérateur.

- **Modifier un événement :** double-cliquez sur la ligne (ou Modifier). La date et le lieu se choisissent au lieu de se taper : exacte, vers, avant, après, entre ; jour, mois, année. Les lieux de l'arbre sont proposés pendant la saisie.
- **Ajouter un événement**, y compris les événements familiaux comme le mariage.
- **Ajouter un parent** (Ctrl+N) : créer parents, conjoint, enfant ou frère/sœur avec nom et premiers événements. Dans le navigateur, cette commande figure aussi dans le menu du clic droit.
- **Saisir parrains, marraines et témoins :** sélectionnez le baptême ou le mariage dans la table des données et cliquez sur **Modifier les parrains et marraines …** ou **Modifier les témoins …** dans la zone de détail en dessous (pour les mariages, aussi dans l'onglet Conjoints/enfants). Ordonnez la liste avec les flèches, ✕ retire une entrée. **Personne de l'arbre …** cherche par nom et lie la personne ; **Sans enregistrement …** ajoute quelqu'un qui n'a pas d'entrée propre – tel qu'inscrit dans le registre paroissial : « Friedrich Plate, cultivateur à Celle ». Le rôle (parrain/marraine, témoin ou un autre comme « sage-femme ») et une note sur la personne se trouvent sous la liste. Les parrains et marraines liés sont enregistrés comme le fait webtrees, les personnes sans enregistrement dans les champs GEDCOM-L _GODP et _WITN ; les sources d'un parrain ou d'une marraine sont conservées. Les parrains et marraines enregistrés seulement sur la personne (exports anciens) sont déplacés dans le baptême d'une simple coche. Nécessite api4webtrees 1.12 ou plus récent.
- **Type de mariage :** dans la boîte de dialogue d'un mariage, **Type** permet de choisir entre non précisé, civil, religieux, partenariat enregistré et union libre. Une seconde cérémonie (comme le mariage religieux après le civil) s'ajoute comme un autre événement « Mariage » de l'union.
- **Supprimer :** un événement ou la personne entière, après confirmation.
- **Notes et médias :** dans l’onglet **Notes**, vous ajoutez des notes générales sur la personne (**+ Nouvelle note**), les modifiez et les supprimez ; les notes des événements figurent en dessous, en lecture. Dans l’onglet **Médias**, vous joignez photos et scans (fichier, média existant, élément des archives ou simplement glissé depuis le gestionnaire de fichiers), changez titre et type et détachez des liens ; un clic sur une image l’ouvre. Nécessite api4webtrees au niveau d’API 23.
- **Modifier dans webtrees :** tout ce que le programme ne sait pas faire lui-même (par exemple les notes partagées), vous le faites sur la page de la personne dans le navigateur. Voir [webtrees dans le navigateur](hilfe:webtrees).

## Fusionner des personnes

![](desktop-zusammenfuehren.jpg)


Les personnes en double apparaissent quand on réunit deux fichiers ou lors de la saisie : le même ancêtre deux fois, une fois comme enfant, une fois comme époux. **Outils › Fusionner des personnes …** (réservé aux gestionnaires de l’arbre, nécessite api4webtrees 1.16 ou plus) liste toutes les paires que le contrôle de plausibilité tient pour des doublons : même nom et une date d’événement identique (règle 219) ou nom semblable avec une année de naissance proche (règle 228). Dans le contrôle, les mêmes paires portent un symbole à deux lignes qui se rejoignent. **Ajouter une paire …** prend deux personnes quelconques, par exemple des enfants du même nom sans données ; dans le tableau des personnes, le bouton droit fait de même.

**Fusionner …** montre qui reste et qui est absorbé, et en dessous seulement ce qui est **ajouté** depuis la seconde personne, en général une à trois lignes à cocher. Les indications identiques ne sont pas reprises deux fois, même si une version a moins de détails (naissance avec date seule à côté d’une naissance avec date et lieu) ou si un « décédé » sans date côtoie un décès daté. **Afficher tous les événements** ouvre la comparaison complète des deux personnes, au cas où tu voudrais aussi retirer quelque chose de la première. Les liens vers les familles et les médias restent toujours, les sources et notes des événements aussi. Tout ce qui pointait vers la seconde personne – familles, citations de sources, parrainages – pointe ensuite vers la première. En dessous, le programme propose d’autres paires (père, mère, conjoints et enfants du même nom) ; les paires cochées suivent après la fusion. Si une personne se retrouve avec deux naissances parce que les données se contredisent, le contrôle le signale et tu corriges dans la fiche de la personne.

**Annuler :** chaque fusion figure dans l’onglet **Journal** avec l’heure et l’utilisateur. **Annuler la fusion** rétablit les deux personnes et tous les liens tels qu’ils étaient – même des jours plus tard, tant que personne n’a modifié les fiches concernées depuis ; sinon le programme nomme les fiches modifiées et ne touche à rien. Sans validation automatique, fusion et annulation attendent un modérateur comme toute modification.

## Photos

![](desktop-gesichter.jpg)


**Ajouter une photo** choisit un fichier sur l'ordinateur et le joint à la personne ; l'image est réduite à la limite d'envoi du serveur. Un clic sur une image ouvre la visionneuse.

## Imprimer et partager

- **Fichier › Imprimer la fiche individuelle …** (Ctrl+P) et **Fiche individuelle en PDF …**
- **Personne › Copier le texte de la personne** (Ctrl+Shift+C) place tous les détails sous forme de texte dans le presse-papiers, p. ex. pour un e-mail ou un traitement de texte.
- **Personne › Rechercher dans la méta-recherche CompGen …** (aussi par clic droit sur une personne) recherche le nom de famille dans les bases de données de l’association allemande de généalogie informatique : GEDBAS, annuaires d’adresses, livres de familles locaux en ligne, listes de pertes et d’autres. Une petite fenêtre montre ce qui est envoyé – seulement le nom de famille sans particule comme « von », un lieu de la personne si vous le souhaitez, jamais de prénoms ni de dates – et les résultats s’ouvrent dans le navigateur. Sans lieu, la recherche trouve généralement davantage.
- Autres sorties : [Listes](hilfe:listen) (fiche individuelle sous forme de liste), [Tableaux](hilfe:tafeln), [Livres](hilfe:buecher).

## GEDCOM-L

wtWin affiche toutes les données de l’addendum GEDCOM-L que contient un arbre (à partir d’api4webtrees 1.21) : dans la zone de détail d’un événement l’adresse, l’identifiant GOV, le code postal (avec date) et le locator Maidenhead du lieu ainsi que le destinataire ; dans l’en-tête de la fiche, au survol de l’identifiant, l’identifiant unique (_UID) ; dans l’onglet **Médias** une étoile sur l’image mise en avant (_PRIM) ; dans la fenêtre des tâches description, catégorie, priorité, type, statut, rappel, date de fin, solution et dépôt d’une tâche de recherche ; dans la gestion des lieux sous **Données** les noms et types antérieurs avec date, les codes postaux avec date, l’identifiant administratif (p. ex. code communal), la population, le locator Maidenhead, la religion et l’identifiant unique. La modification conserve toutes les données GEDCOM-L. On peut modifier la **situation familiale** d’un couple (onglet Partenaires/Enfants, choix au-dessus des événements), l’**image principale** (onglet Médias, « Image principale » – la place en premier et la marque avec _PRIM) et tous les champs d’une **tâche de recherche** dans la fenêtre de la tâche (à partir d’api4webtrees 1.21).
