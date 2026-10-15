# gedaudit

**gedaudit** vérifie un fichier GEDCOM à la recherche de contradictions et d’anomalies, avec les mêmes règles que la vérification de cohérence de wtWin, wtTux et wtMac. Il n’a besoin ni de webtrees ni d’un serveur : le fichier est seulement lu, rien n’est modifié, rien ne quitte l’ordinateur.

## Ouvrir un fichier

**Fichier › Ouvrir un fichier GEDCOM …** (Ctrl+O) ou le bouton de la page d’accueil. Les fichiers récemment ouverts figurent sur la page d’accueil et sous **Fichier › Récemment ouverts**. Un fichier `.ged` s’ouvre aussi avec gedaudit par double-clic dans le gestionnaire de fichiers.

Sont lus GEDCOM 5.5, 5.5.1 et 7. Le jeu de caractères vient du fichier (UTF-8, UTF-16, ANSEL, ANSI) ; celui reconnu est indiqué dans la ligne au-dessus des résultats. Si le fichier a été modifié dans l’autre programme, **Recharger le fichier** (F5) reprend le nouvel état.

## Vérifier

À gauche les règles par groupes, à droite les résultats. Le fonctionnement des règles, des préréglages, des limites et du pointage est décrit au chapitre [Vérification de cohérence](hilfe:pruefung). La différence avec wtWin : pas de fiche de personne et pas de modification, car gedaudit n’écrit pas dans le fichier. On corrige dans le programme de généalogie d’où vient le fichier ; puis on exporte de nouveau et on recharge dans gedaudit.

## Forme du fichier

Le groupe **Forme** n’existe que pour les fichiers : lignes qui ne sont pas du GEDCOM, liens vers des personnes ou familles inexistantes, identifiants en double, dates sous une forme illisible, personnes sans nom, plus d’une naissance ou d’un décès, liens familiaux enregistrés d’un seul côté, en-tête manquant ou jeu de caractères de l’en-tête différent du fichier, balises hors du standard 5.5.1 ou 7. Les deux dernières règles sont désactivées par défaut, car certains programmes les déclenchent très souvent.

**Affichage › Structure du fichier …** (F6) montre l’arbre des balises par type d’enregistrement avec le nombre d’occurrences. Les balises d’éditeur (avec tiret bas) et les balises hors standard sont colorées. On voit ainsi ce qu’un programme produit à l’export et ce qu’un autre perdra à l’import.

## Comparer deux fichiers

**Fichier › Comparer avec un autre fichier …** (Ctrl+D) met un deuxième fichier en face du fichier ouvert, par exemple un état plus ancien, l’export d’un autre programme ou le fichier d’un collègue chercheur. Les personnes sont appariées par identifiant, sinon par nom, année de naissance et sexe ; les correspondances proches figurent sous « probablement la même personne ». À gauche les groupes : modifié, probablement la même, seulement dans l’un ou l’autre fichier, inchangé. Un clic sur une entrée montre les champs modifiés et les deux personnes côte à côte. Les parents sont comparés par nom, car les identifiants peuvent différer d’un fichier à l’autre. Imprimer et PDF donnent la liste des différences. Rien n’est fusionné ni écrit.

## Voir une personne

Un clic sur un résultat affiche en bas la **personne** : événements avec date, lieu et nombre de sources, les parents, les conjoints avec mariage et les enfants. Si le résultat concerne deux personnes, par exemple un doublon possible ou un enfant et sa mère, les deux sont affichées côte à côte. Les identifiants (I123, F45) sont ceux du fichier, pour retrouver la personne dans le programme d’origine. La ligne de séparation au-dessus de la fiche peut être déplacée. **Ctrl+C** copie le texte de la personne choisie (nom, identifiant, événements, parents, conjoints, enfants) tant qu’aucun champ de saisie n’a le focus ; un clic droit sur un résultat ou un événement propose **Copier le texte de la personne** et **Copier la ligne**, et les valeurs de la fiche se sélectionnent à la souris. Le clic droit propose aussi **Rechercher dans la méta-recherche CompGen …** : le nom de famille et, si vous le souhaitez, un lieu sont envoyés à meta.genealogy.net, et les résultats de GEDBAS, des livres de familles locaux en ligne et d’autres bases s’ouvrent dans le navigateur.

## Pointer

Les résultats que vous avez examinés et qui restent tels quels se pointent ; ils disparaissent de la liste et ne reviennent que si quelque chose change à cet endroit. La liste des pointages est conservée sur ce PC, séparément par fichier (selon son chemin). **Enregistrer les pointés …** et **Charger les pointés …** l’échangent sous forme de fichier, aussi avec wtWin.

## Imprimer

**Fichier › Imprimer** (Ctrl+P) et **Enregistrer en PDF …** sortent la liste actuelle des résultats avec un résumé.

## Langue et apparence

**Affichage › Langue** change l’interface (allemand, anglais, français, néerlandais, espagnol), **Affichage › Apparence** passe du clair au sombre.

## GEDCOM-L

gedaudit connaît l’addendum GEDCOM-L à GEDCOM 5.5.1. Dans la fenêtre **Structure du fichier**, ses tags (_LOC, _GODP, _UID …) sont marqués « GEDCOM-L » et non comme tags d’éditeur ; la ligne d’en-tête indique la version de l’addendum si le fichier la donne. La fiche de la personne montre les parrains et témoins sans enregistrement propre (_GODP, _WITN), l’adresse ainsi que l’identifiant GOV, le code postal et le locator Maidenhead du lieu. Les renvois vers des enregistrements de lieu (`3 _LOC` au lieu, `1 _LOC` dans l’enregistrement) dont la cible manque sont signalés comme tout autre renvoi dans le vide.
