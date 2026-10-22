# La fenêtre principale

Sous **Affichage › Disposition**, vous choisissez entre quatre dispositions. Toutes montrent le même arbre, organisé différemment.

## Navigateur (par défaut)

![](desktop-navigator.png)


La barre d'outils en haut, en dessous la **personne centrale** avec ses conjoints et ses enfants, à droite ses ancêtres, génération par génération.

- **Un clic** sur une personne en fait la personne centrale.
- **Un double-clic** ouvre la [fiche individuelle](hilfe:person).
- **Bouton droit de la souris** : afficher comme personne centrale, modifier, ajouter aux favoris, ouvrir dans webtrees.
- La **fratrie** figure dans l'encadré en haut à gauche (demi-frères et demi-sœurs avec ½) ; un clic en fait la personne centrale.
- **Clavier :** flèche droite vers le père (avec Shift vers la mère), gauche vers l'enfant, haut/bas pour parcourir la fratrie, Enter ouvre la fiche (voir [Raccourcis clavier](hilfe:tasten)).
- Les **générations** (2 à 7) et le **zoom** (−, +, Ctrl+molette, ajuster) se règlent dans le navigateur lui-même ; le choix est mémorisé. Les cases rétrécissent vers l’extérieur ; avec 7 générations, la rangée extérieure est disposée en quinconce.
- Un parent manquant apparaît comme « Père inconnu » ou « Mère inconnue ». Avec les droits de modification, **Ajouter un parent** crée la personne manquante.

**Barre d'outils :** Aller à, Fiche individuelle, Ajouter un parent, Favoris, Retour, Suivant, Historique, Personne de départ, Liste, Tableau, Imprimer, Accueil, Arbre (selon la disposition : Navigateur, Famille ou Livre de familles du lieu), Photos, Archives, Vérification, Lieux, Sources, Tâches de recherche, Modifications récentes, webtrees – Archives, Tâches de recherche et Modifications récentes seulement si le serveur les connaît. Langue, Aide et Quitter se trouvent dans le menu. Si la largeur ne suffit pas, seules les icônes s'affichent (le nom apparaît au survol), et pour finir le reste passe dans le menu **Plus**. Désactivez les libellés sous **Affichage › Libellés de la barre d'outils**.

## Arbre au centre

![](desktop-baum-mittelpunkt.jpg)
![](desktop-baum-vollbild.jpg)


Trois colonnes : à gauche la **liste des personnes** avec un champ de recherche (Ctrl+F y place le curseur), au milieu l'**arbre** en sablier autour de la personne centrale (faire glisser, zoomer avec la molette, déplier les branches vers le haut), à droite le **panneau de la personne** avec les détails de la personne sélectionnée.

En dézoomant, une case montre moins de choses plutôt que de rapetisser : d'abord sans image ni années, puis seulement le prénom, enfin une case dans la couleur du sexe. Clic droit dans la liste : comme personne centrale, profil, ajouter aux favoris, ouvrir dans webtrees.

## Vue famille

![](desktop-familienansicht.jpg)


Le milieu montre la **famille** de la personne centrale, disposée comme dans les logiciels de généalogie classiques : en haut les ancêtres des deux conjoints sur **2 à 4 générations** (− et + en haut à droite, mémorisé), en dessous les parents, au milieu le couple sous forme de grandes cartes avec portrait, naissance, décès et profession, entre les deux la ligne d'état avec tous les mariages et le nombre d'enfants, en bas les **enfants avec leurs conjoints** et en dessous les **petits-enfants**. Des lignes continues relient les générations. Si la personne centrale a plusieurs mariages, chacun a son propre **onglet**.

- **Ajuster à la fenêtre :** la famille est agrandie jusqu'à remplir la fenêtre (jusqu'à 180 %) ; le pourcentage est affiché en haut à droite. **Ctrl+molette** change le zoom, un clic sur le pourcentage réajuste.
- **Un clic** sélectionne une personne ; le panneau de la personne à droite l'affiche. **Un double-clic** en fait la personne centrale – c'est ainsi que vous parcourez les familles vers le haut (parents, grands-parents) et vers le bas (enfants, petits-enfants).
- **Boutons dans les cartes du couple :** Fiche individuelle, Photos, Ajouter un parent. **+ Fils** et **+ Fille** dans la ligne d'état ajoutent un enfant de cette union avec le sexe prérempli. Au-dessus de la famille : **+ Ajouter une personne ▾** (père, mère, conjoint, enfant ou **rattacher une personne existante**, c'est-à-dire relier une fiche existante au lieu d'en créer une), **Aller à** et les générations. À la création, le dialogue avertit si une personne du même nom existe déjà et propose « Rattacher au lieu de créer ».
- **Bouton droit de la souris** sur chaque carte : Mettre au centre, Fiche de la personne, Ajouter un parent, Ouvrir dans webtrees, Supprimer la personne (avec droits de modification, après confirmation).
- Un parent manquant apparaît comme « Père inconnu » ou « Mère inconnue » ; avec les droits de modification, **+ ajouter** le crée.
- La **fratrie** de la personne centrale est affichée au-dessus du couple. **Clavier :** flèche haut vers le père (avec Shift vers la mère), bas vers l'enfant, gauche/droite pour parcourir la fratrie, Tab vers l'onglet suivant.

La [table des personnes](hilfe:tabelle) (Ctrl+4) montre tout le monde sous forme de tableau.

## Livre de familles du lieu

![](desktop-ortsfamilienbuch.jpg)


Le livre de familles comme vue vivante : à gauche le **filtre de lieu** (vide = toutes les familles) et les familles numérotées, par ordre alphabétique ou chronologique, au centre les entrées exactement comme dans le livre imprimé – époux avec renvoi à la famille des parents, mariage, enfants avec renvoi à leur propre famille, sources, parrains, notes. Les renvois <7>, [12] et H3 mènent à l’entrée, un clic sur un nom affiche la personne dans le panneau de droite, un double clic ouvre la fiche de personne ; après l’enregistrement, le livre se reconstruit. À l’écran, sources et parrains n’apparaissent que sur demande (cases au-dessus de la liste des familles) ; l’impression les a toujours. La liste des personnes à gauche reste masquée dans cette disposition, la liste des familles la remplace. À droite les **maisons et fermes** du lieu avec historique, habitants et propriétaires (à partir d’api4webtrees 1.15). Vous lisez ainsi le livre pendant la saisie et voyez les lacunes avant l’impression. Impression et site : **Créer › Créer un livre › Livre de familles**.

## Sources

![](desktop-quellen.jpg)


**Affichage › Sources** (Ctrl+5) ou l'icône **Sources** ouvre la gestion des sources : à gauche toutes les sources de l'arbre avec recherche et nombre de citations, à droite la source sélectionnée avec auteur, publication, dépôt d'archives et cote, texte, notes, médias et **Citée par** – chaque individu et chaque famille avec les faits qui portent la citation. Un clic sélectionne la personne, un double-clic en fait la personne centrale.

Avec les droits de modification : **+ Nouvelle source** (titre, auteur, publication, abréviation, dépôt d'archives avec cote – y compris un nouveau dépôt –, texte, note), **Modifier**, **Ajouter un scan ou un fichier …** (joint le fichier à la source comme document) et **Supprimer** (avec un avertissement si la source est encore citée). **Retirer les sources inutilisées …** liste toutes les sources que personne ne cite, à cocher puis supprimer. Dans la boîte de dialogue de citation, **Nouvelle source …** crée directement une source et **Source depuis un fichier …** en crée une à partir d'un scan : titre tiré du nom de fichier, fichier joint, source aussitôt sélectionnée.

Les **documents** se joignent à deux endroits, comme dans webtrees : à la **source** (le registre paroissial numérisé) ou à la **citation** elle-même (le scan de ce baptême précis). Aux deux endroits, vous pouvez **envoyer un fichier** ou choisir un **média existant** de l'arbre ; « détacher » ou ✕ supprime seulement le lien, l'objet média et le fichier sont conservés.

Dans la fiche individuelle, la zone de détail sous la table des événements (onglet **Sources**) montre chaque citation avec page, qualité, date, extrait, notes et médias ; un clic sur le titre ouvre la source ici. Une source sans enregistrement propre (« selon Martha Meier ») est affichée en italique.

Avec les droits de modification, les boutons en dessous sont **+ Citer une source** (chercher une source dans la gestion des sources ou la saisir en texte, avec page, qualité, date, extrait et note), **Modifier**, **Retirer**, **▲ ▼** (ordre) et **Copier vers …** – la même citation pour d'autres événements de cette personne, pour les parents, conjoints et enfants (comme citation générale sur l'enregistrement) ou pour l'union. Seul ce que vous saisissez est modifié ; tout le reste de la citation et de l'événement est conservé.

Dans les dispositions **Arbre au centre** et **Vue famille**, le bouton **Sources** se trouve dans la barre du haut ; dans le panneau de la personne à droite (onglet Événements), chaque citation est cliquable et ouvre la source.

Nécessite api4webtrees avec le niveau d'API 18 ; avec des serveurs plus anciens, l'icône ouvre comme avant la liste des sources de webtrees.


## Lieux

![](desktop-ortskarte.jpg)
![](desktop-hof-geschichte.jpg)


**Affichage › Lieux** (Ctrl+6) ou l’icône **Lieux** ouvre la gestion des lieux (à partir d’api4webtrees 1.13) : à gauche tous les lieux tels qu’ils figurent dans les événements, avec recherche, nombre d’événements et ◉ pour « coordonnées connues ». À droite le lieu sélectionné en onglets : **Personnes** (chaque individu et chaque famille avec leurs événements à cet endroit – un clic sélectionne la personne, un double-clic en fait la personne centrale), **Histoire** (tous les événements du lieu par ordre chronologique – les événements du lieu lui-même comme un incendie ou une reconstruction mis en évidence, plus les naissances, résidences, mariages et décès des personnes et familles ici), **Données** (niveaux, type, lieu supérieur, lieux inclus, fiche de lieu et identifiant GOV), **Notes**, **Sources**, **Médias** et **Coordonnées** avec carte. Dans webtrees, la note, l’identifiant GOV et les coordonnées d’un lieu se trouvent dans sa fiche de lieu (_LOC, GEDCOM-L) ; sans fiche, wtWin prend les coordonnées des données géographiques de webtrees ou d’un événement.

**Maisons et fermes :** GEDCOM-L décrit les bâtiments comme des fiches de lieu à part entière avec un type (TYPE ferme, maison …) et un lieu supérieur (à partir d’api4webtrees 1.15). Ces fermes apparaissent sous leur lieu comme « lieux inclus » avec leur type, même sans événement enregistré ; leur onglet **Histoire** est la chronique de la ferme avec habitants et propriétaires. Le type se définit sous **Modifier**. À côté du type, on peut choisir le **type GOV** (ferme 24, moulin 87 …) ; il est enregistré comme `2 _GOVTYPE` dans la fiche de lieu et désigne la nature du lieu sans ambiguïté, même si le texte dit « Bauernhof » ou « farm » (à partir d’api4webtrees 1.19). Les événements qui reçoivent un lieu obtiennent le renvoi `3 _LOC` vers sa fiche de lieu lorsqu’elle est sans ambiguïté – d’autres programmes, comme la liste des fermes de l’Online-OFB, ne trouvent la ferme que par ce renvoi. Si des événements d’un lieu n’ont pas encore de renvoi, **Modifier** propose de les relier. Changer le lieu supérieur conserve les rattachements antérieurs datés et ecclésiastiques.

L’onglet **Données** est la page du lieu : en haut une image (votre propre photo sur la fiche de lieu, sinon une suggestion de Wikimedia Commons avec crédit), des tuiles pour naissances, mariages, décès et autres événements, la **hiérarchie** de GOV (aujourd’hui et autrefois, avec les années) et **Consulter** avec GOV, GenWiki, Wikipédia, Archion, Matricula, Archivportal-D et la Bibliothèque numérique allemande. Pour cela, le nom du lieu et l’identifiant GOV sont envoyés à gov.genealogy.net et à Wikimedia ; les réponses restent 7 jours sur ce PC. Une image de Wikimedia n’est affichée que si le résultat se trouve à 30 km au plus des coordonnées du lieu. En dessous, jusqu’à six autres images de Wikimedia Commons ; un clic les affiche en grand (flèches pour feuilleter), avec auteur, licence et lien vers la source. Ces images sont seulement affichées, pas enregistrées dans l’arbre.

**Carte** (bouton au-dessus de la liste) montre tous les lieux avec coordonnées : taille et couleur selon le nombre d’événements, lieux proches en groupe gris avec leur nombre – un clic agrandit. Un clic sur un lieu le sélectionne, **Afficher** passe à ses données. La recherche filtre aussi la carte ; le nombre de lieux encore sans coordonnées s’affiche en haut à gauche. Zoomez avec la molette, un double-clic (avec Maj pour dézoomer) ou les boutons **+** et **−** en haut à droite ; cela vaut pour toutes les cartes de wtWin.

Avec le droit de modification, **Modifier** ouvre les données du lieu : identifiant GOV (avec **Chercher dans GOV**), note et coordonnées. **Rechercher les coordonnées …** interroge OpenStreetMap avec le nom du lieu ; un clic sur un résultat reprend latitude et longitude. Tout est enregistré dans la fiche de lieu (_LOC), que wtWin crée si nécessaire ; si le nom du lieu apparaît plusieurs fois dans l’arbre, les événements de ce lieu reçoivent un renvoi vers elle. Les administrateurs peuvent aussi écrire les coordonnées dans les données géographiques de webtrees – seules celles-ci sont lues par les cartes du navigateur.

**Renommer et fusionner :** le nom du lieu en haut est un champ de saisie. Modifiez-le et confirmez avec la coche (ou Entrée) – auparavant, wtWin indique combien d’événements seront modifiés et quels lieux inclus suivent (« Kortau, Allenstein » → « Kortau, Olsztyn »). Si le nouveau nom existe déjà, les deux lieux sont fusionnés : notes, sources et médias des fiches de lieu sont conservés ; si l’identifiant GOV ou les coordonnées diffèrent, ceux de la cible s’appliquent. Les événements verrouillés ou confidentiels que vous ne pouvez pas modifier gardent l’ancien nom. Sans acceptation automatique, les modifications attendent un modérateur comme d’habitude. Conseil : corrigez les fautes de frappe, mais laissez les noms historiques (Allenstein/Olsztyn) et reliez-les par l’identifiant GOV.

**Nettoyer les lieux :** « Nettoyer … » au-dessus de la liste des lieux, **Outils › Nettoyer les lieux …** (ou « Nettoyer les lieux … » dans le contrôle de plausibilité, règle 510) montre les noms qui désignent probablement le même lieu : même identifiant GOV, graphie à la casse et aux espaces près, même lieu sans hiérarchie (« Hermannsburg » à côté de « Hermannsburg, Celle, … »), variantes orthographiques (« Bienenbuettel »/« Bienenbüttel ») et lieux homonymes à moins de 2 km. La proposition est de fusionner le plus rare dans le plus fréquent ; ⇅ inverse le sens. « Fusionner … » demande confirmation avec les chiffres, comme pour renommer. « Garder séparés » mémorise la paire pour cet arbre – par exemple des noms historiques avec le même identifiant GOV. En haut figure le nombre de lieux avec fiche de lieu dont des événements n’ont pas de renvoi ; **Créer les renvois** les relie tous (à partir d’api4webtrees 1.19). **Adresses en maisons …** (à partir d’api4webtrees 1.20) rassemble les événements qui portent un lieu et une adresse – c’est ainsi que beaucoup de programmes gardent la ferme sous le domicile – et fait de chaque adresse un lieu à part entière sous le lieu actuel (« Klosterstraße 3, Mengen ») avec fiche de lieu, type et renvoi ; la ligne d’adresse disparaît, date, sources et notes restent.

Les noms de lieux dans le panneau de la personne et sur la fiche de la personne sont cliquables, et chaque champ de lieu rempli a une icône de lieu – les deux ouvrent le lieu directement dans la gestion des lieux.

Avec le droit de modification, vous écrivez la note directement dans l’onglet **Notes**, joignez photos et documents dans l’onglet **Médias** (téléverser un fichier, un média existant ou un élément des archives ; **détacher** supprime seulement le lien) et citez une source pour le lieu dans l’onglet **Sources**. Tout va dans la fiche de lieu ; s’il n’y en a pas encore, wtWin la crée. La première photo de la fiche de lieu devient l’image en haut de la page du lieu.

Les coordonnées se saisissent en décimal ou en degrés, minutes, secondes ; **Depuis le presse-papiers** reprend une paire copiée, par exemple depuis Wikipédia. Avec **Rechercher les coordonnées …**, deux cases indiquent si un résultat fournit les coordonnées et/ou code postal, région et pays. Dans l’onglet **Coordonnées**, des boutons ouvrent le lieu dans OpenStreetMap, Bing Maps ou Google Maps ; **Centrer la carte** le ramène au milieu. Dans l’onglet **Personnes**, le pointeur montre dates et événements ; un double-clic ouvre la fiche de la personne.

**Modifier** accepte aussi un **nom court** (GEDCOM-L : abréviation du nom du lieu). Les livres avec « Abréger les noms de lieux » l’utilisent à la place du nom complet ; sans nom court, ils prennent comme avant la première partie du nom.

Dans l’onglet **Médias**, le pointeur sur une image montre titre, nom de fichier, format, type, taille et dimensions ; **modifier** change titre et type (photo, document, carte postale …). On peut aussi glisser des fichiers depuis le gestionnaire de fichiers sur l’onglet.

## Tâches, favoris et modifications récentes

Nécessite api4webtrees 1.18 ou plus sur le serveur ; l’arbre sur ce PC l’inclut.

- **Tâches de recherche** (Affichage › Tâches de recherche, Ctrl+7) : toutes les tâches de l’arbre – les mêmes que webtrees montre dans le navigateur dans le bloc « Tâches de recherche », enregistrées dans le champ GEDCOM `_TODO` d’une personne ou d’une famille. Chacune avec texte, date, responsable et note. **Nouvelle tâche …** en ajoute une pour la personne centrale, comme Personne › **Ajouter une tâche …** ; dans le contrôle de plausibilité, le symbole de tâche transforme un résultat en tâche avec le texte du contrôle. Une date future est un rappel et disparaît de la liste avec « seulement les échues ». **Terminé** supprime la tâche, comme le fait webtrees.
- **Favoris** (Ctrl+B) : depuis la 1.37 ce sont les favoris de webtrees, visibles aussi dans le navigateur sous « Ma page › Mes favoris ». Une ancienne liste est reprise au premier appel. Les gestionnaires peuvent définir des favoris pour tous avec Personne › **Mettre en favori pour l’arbre** ; ils apparaissent en second groupe.
- **Modifications récentes** (Affichage › Modifications récentes, Ctrl+8) : qui a créé, modifié ou supprimé quelle fiche et quand, y compris les modifications pas encore validées. Un clic met la personne au centre, un double-clic ouvre la fiche.
- **Ordre :** dans la fiche de la personne, onglet Conjoints/enfants, les flèches ▲▼ déplacent unions et enfants vers le haut ou le bas. Seul l’ordre des lignes dans le GEDCOM change.

## Sections : Accueil, Arbre, Photos

- **Accueil** (Ctrl+1) : message d'accueil, anniversaires à venir, modifications récentes de l'arbre, la personne de départ. Les modérateurs voient ici les modifications en attente et les acceptent ou les refusent.
- **Arbre** (Ctrl+2) : navigateur, vue de l'arbre ou famille.
- **Photos** (Ctrl+3) : toutes les images de l'arbre ; un clic ouvre la visionneuse. Si le module Sammlungen (collections) tourne sur le serveur, ses archives apparaissent aussi ici.

## Affichage

- **Apparence :** clair, sombre ou comme le système.
- **Langue :** comme le système ou fixée sur allemand, anglais, français, néerlandais ou espagnol. Prend effet immédiatement ; les libellés du serveur (types d'événements, lieux) arrivent dans la nouvelle langue au prochain rechargement. L'aide et la vérification de cohérence changent de langue elles aussi.
- **Affichage des lieux … :** comment les noms de lieux apparaissent dans les vues, listes et tableaux : complet, ville et pays, ville et code pays (NL ou NLD). Avec un pays d’origine, les lieux de ce pays n’affichent que la ville (« Amsterdam » au lieu de « Amsterdam, Noord-Holland, Nederland »), les autres suivent la forme choisie. Le pays est la dernière partie du nom ; les noms de pays dans les cinq langues et les codes eux-mêmes sont reconnus. Les champs de saisie, la gestion des lieux et les livres gardent le nom complet.
- **Code couleur :** colore les ancêtres de la personne de départ selon les quatre lignées des grands-parents et leurs descendants dans une cinquième couleur (d'après Mary Hill). Nécessite une personne de départ.
- **Afficher la fratrie et ses conjoints**, **Afficher les cousins**, **Afficher les conjoints des cousins** : pour la vue de l'arbre ; les conjoints des cousins seulement si les cousins sont affichés.
- **Générations :** combien de générations d'ancêtres l'arbre charge.

## Retour, suivant, historique

Chaque changement de personne centrale entre dans l'historique. **Alt+Left** revient en arrière, **Alt+Right** avance de nouveau ; l'icône **Historique** liste les dernières personnes. **Alt+Home** saute à la personne de départ que webtrees connaît pour votre compte.

## Barre d'état

En bas : serveur, compte (ou « invité ») et version du programme. **F5** recharge l'arbre, p. ex. après des modifications faites dans le navigateur.

## Outils (menu Outils)

**Outils › Calculateur de dates** calcule l’écart entre deux dates en années, mois et jours, la date de naissance d’après la date de décès et l’âge (« âgé de 63 a. 4 m. 2 j. ») et convertit une date entre calendriers julien et grégorien, avec le jour de la semaine et le jour de l’année liturgique. **Outils › Année liturgique et fêtes** affiche Pâques et tous les dimanches et fêtes d’une année (noms allemands et latins, protestant ou catholique, grégorien ou julien), trouve la date de « Laetare 1734 » ou « Dom. III p. Trin. » et indique quel dimanche ou quelle fête correspond à une date. Les deux calculent sur ce PC sans serveur ; les résultats se copient comme dates GEDCOM.
