# Vérification de cohérence

**Outils › Vérification de cohérence …** (Ctrl+Shift+P) ou l'icône **Vérification** contrôle tout l'arbre, dans la mesure où vous pouvez le voir, avec 77 règles. Les **erreurs** sont des contradictions dans les données (décès avant la naissance, propre ancêtre), les **avertissements** signalent l'inhabituel mais possible (mère très jeune, parrain déjà décédé, doublon possible, variante de lieu). Avec api4webtrees 1.11 ou plus récent, les règles sur les parrains et marraines (024, 126) utilisent directement les parrains, marraines et témoins liés et recherchent par leur nom ceux saisis en texte libre ; avec un module plus ancien, elles lisent la note « Paten: … ».

![](desktop-pruefung.jpg)


## Règles et limites

Les règles sont regroupées à gauche : **Chronologie**, **Limites d'âge**, **Structure**, **Noms**, **Sources**, **Lieux**, **Forme**. Le groupe Forme vérifie la structure d’un fichier GEDCOM (liens, forme des dates, mentions obligatoires) ; dans wtWin, seules les règles visibles dans l’arbre s’appliquent. Un clic sur une règle n'affiche que ses anomalies, **Toutes les anomalies** affiche de nouveau tout.

- **Préréglage :** de strict à large ; « Sources » active en plus les règles 420 et 421 (événements et individus sans source). Dès que vous modifiez quelque chose, il devient « Réglages personnels ».
- Pour chaque règle : **limite** (années ou mois, la valeur par défaut est indiquée), **gravité** (erreur ou avertissement), ou désactiver la règle.
- **Estimer :** déduire la naissance et le décès manquants du baptême et de l'inhumation, afin que les règles d'âge s'appliquent.
- **Toutes les règles et limites par défaut** réinitialise, **Vérifier à nouveau** recalcule avec les réglages actuels.

## Travailler avec les anomalies

Chaque anomalie nomme la personne, la règle et les détails concernés.

- Un clic sur l'anomalie ouvre la personne ; **Modifier l'événement** change la date ou le lieu directement depuis l'anomalie (avec les droits de modification).
- **Cocher comme vérifiée :** les anomalies que vous avez vérifiées et qui restent telles quelles (p. ex. une naissance tardive documentée) disparaissent de la liste. **Afficher les cochées** les fait réapparaître, **Rouvrir** retire la coche.
- La liste des coches est enregistrée sur cet ordinateur, séparément par serveur et par arbre. **Exporter les coches …** et **Importer des coches …** l'échangent sous forme de fichier, p. ex. avec un autre chercheur ou pour un second ordinateur.

## Imprimer

**Imprimer** et **Enregistrer en PDF …** produisent la liste actuelle des anomalies avec un résumé. La ligne au-dessus indique combien de personnes et de familles ont été vérifiées et combien d'anomalies sont des erreurs.
