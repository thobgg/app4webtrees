# Arbre généalogique sur cet ordinateur

Sans serveur, wtWin crée l'arbre généalogique sur cet ordinateur. En arrière-plan tourne un webtrees complet avec PHP intégré, inchangé par rapport à la version officielle. Il n'est accessible que depuis cet ordinateur, démarre avec le programme et s'arrête avec lui. Vous ne voyez ni serveur ni mot de passe ; le programme se connecte tout seul.

## Créer

Au premier démarrage, à droite : saisissez un nom et cliquez sur **Créer l'arbre généalogique** (vide) ou sur **Importer depuis un fichier GEDCOM …**. Pour venir d'un autre programme, exportez-y un fichier GEDCOM (`.ged`) et choisissez-le ici. Les personnes, familles, événements, sources et notes sont importés. Le fichier GEDCOM lui-même reste inchangé ; un second import crée un autre arbre à côté et n'écrase jamais rien.

Le fichier peut être en UTF-8 (avec ou sans indicateur d'ordre des octets), UTF-16, ANSEL ou ANSI ; le jeu de caractères est détecté et converti comme lors de l'import dans webtrees. Les enregistrements isolés inutilisables (comme des ID en double) sont ignorés et listés dans `import.log`, le reste est importé.

Les photos ne sont pas reprises du GEDCOM. Ajoutez-les dans le programme, ou copiez-les plus tard dans le dossier des médias (voir ci-dessous) et liez-les dans webtrees.

## Plusieurs arbres

**Fichier › Arbres sur ce PC …** affiche tous les arbres avec leur nombre de personnes. Vous pouvez en ouvrir un, le renommer (modifier le titre, puis la coche ou Entrée) ou le supprimer (jamais le dernier), créer un autre arbre vide ou importer un autre fichier GEDCOM. Les restes vides d’imports échoués – seulement la personne d’exemple « John Doe » de webtrees – sont supprimés automatiquement au démarrage ; un arbre que vous avez créé vide vous-même est conservé.

## Où se trouvent les données

| Système | Dossier |
| - | - |
| Windows | `%LOCALAPPDATA%\app4webtrees` (à taper dans la barre d'adresse de l'Explorateur) |
| Linux | `~/.local/share/app4webtrees` |

On y trouve `webtrees/` avec le programme et `webtrees/data/` avec la base de données (SQLite) et le dossier des médias `media/`. Le journal du serveur PHP est `php.log`. Ce que wtWin remarque lui-même (connexions interrompues, nouvelles tentatives, redémarrages du serveur) est noté dans `wtwin.log`, juste à côté.

## Archives

L'arbre généalogique sur cet ordinateur est livré avec le module **Sammlungen** (collections) : photos et documents sont rangés en dossiers sous `data/media`, n'ont pas besoin d'être rattachés à des individus et apparaissent sous **Photos › Archives** ainsi que dans webtrees dans le navigateur. Les scans de registres paroissiaux des archives peuvent être attribués comme source ou citation dans la gestion des sources.

## Sauvegarde

Le programme ne fait pas de sauvegarde automatique. Deux possibilités :

- wtWin étant fermé, copiez le dossier `app4webtrees`, p. ex. sur une clé USB. C'est la sauvegarde complète, photos comprises.
- **Ouvrir webtrees dans le navigateur** et exporter l'arbre en GEDCOM dans le panneau de configuration. Cela sauvegarde les données, pas les images.

## Tout ce que propose webtrees

**Fichier › Ouvrir webtrees dans le navigateur** affiche votre webtrees dans le navigateur : panneau de configuration, modules, modification des noms, création de sources. Le navigateur demande sa propre connexion. Le nom d'utilisateur est votre nom de connexion sur l'ordinateur ; le mot de passe a été généré au hasard à la création de l'arbre et enregistré dans le fichier `zugang.properties` du dossier `app4webtrees` (voir ci-dessus). Ouvrez le fichier avec un éditeur de texte et copiez le mot de passe. Ne le communiquez pas ; c'est l'accès gestionnaire à votre arbre.

## Passer sur un NAS ou chez un hébergeur web

Si la famille doit pouvoir consulter l'arbre ou si vous travaillez sur deux ordinateurs, l'arbre passe sur un serveur, p. ex. un Synology avec nas4webtrees. Ensuite, vous utilisez wtWin comme avant, simplement connecté.

1. Dans wtWin, **Ouvrir webtrees dans le navigateur**, puis **Panneau de configuration › Arbre généalogique › Exporter** en GEDCOM.
2. Photos : copiez le dossier `webtrees/data/media` (voir ci-dessus) dans `data/media` du webtrees sur le serveur.
3. Sur le serveur, créez un nouvel arbre et importez le GEDCOM.
4. Dans wtWin, **Fichier › Se déconnecter**, **Autre adresse**, saisissez l'adresse du serveur. Ou cliquez sur **Connect with wtWin** sur la page **App** du serveur.

L'arbre sur l'ordinateur est conservé jusqu'à ce que vous supprimiez le dossier.

## En cas de problème

Si l'arbre ne peut pas être créé, le programme affiche un message avec le chemin de `php.log`. Merci de le signaler sur github.com/thobgg/app4webtrees/issues en joignant le fichier, et lors de l'import d'un fichier GEDCOM également `import.log` du même dossier (jeu de caractères, enregistrements ignorés, cause de l'échec). **Aide › À propos de wtWin** indique si PHP et webtrees ont été trouvés.

**« Connexion … interrompue » :** si la connexion à l’arbre sur ce PC se coupe alors que webtrees fonctionne dans le navigateur, un autre programme filtre généralement le trafic réseau de wtWin – blocage de publicités ou protection web, aussi la protection en temps réel d’un antivirus. Une exception pour wtWin (`%LOCALAPPDATA%\Programs\wtWin\wtWin.exe`) dans ce programme aide généralement. Ce que wtWin remarque est noté dans `wtwin.log`.

**Personne de départ :** si un arbre sur ce PC n'a pas encore de personne de départ, le programme demande une fois à l'ouverture « Par qui l'arbre doit-il commencer ? » – rechercher la personne et cliquer. Le choix devient l'individu par défaut de l'arbre ; à modifier sous Personne › Définir comme personne de départ …
