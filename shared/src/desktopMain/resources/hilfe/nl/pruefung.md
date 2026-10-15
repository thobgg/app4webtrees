# Plausibiliteitscontrole

**Extra › Plausibiliteitscontrole …** (Ctrl+Shift+P) of het symbool **Controle** controleert de hele stamboom, voor zover u die mag zien, met 77 regels. **Fouten** zijn tegenstrijdigheden in de gegevens (overlijden voor geboorte, eigen voorouder), **waarschuwingen** zijn ongewoon, maar mogelijk (zeer jonge moeder, doopgetuige al overleden, mogelijke dubbele persoon, plaatsvariant). Met api4webtrees 1.11 of nieuwer gebruiken de doopgetuigenregels (024, 126) direct de gekoppelde doop- en huwelijksgetuigen en zoeken ze die als vrije tekst op naam op; met een oudere module lezen ze de notitie „Getuigen: …“.

![](desktop-pruefung.jpg)


## Regels en grenzen

De regels staan links in groepen: **Chronologie**, **Leeftijdsgrenzen**, **Structuur**, **Namen**, **Bronnen**, **Plaatsen**, **Vorm**. De groep Vorm controleert de opbouw van een GEDCOM-bestand (koppelingen, datumvorm, verplichte gegevens); in wtWin gelden daarvan alleen de regels die ook in de stamboom zichtbaar zijn. Een klik op een regel toont alleen haar meldingen, **Alle meldingen** toont weer alles.

- **Voorinstelling:** van streng tot soepel; „Bronnen“ schakelt bovendien de regels 420 en 421 in (gebeurtenissen en personen zonder bron). Zodra u iets wijzigt, wordt het „Eigen instellingen“.
- Per regel: **Grens** (jaren of maanden, de standaardwaarde staat erbij), **Ernst** (fout of waarschuwing), of de regel uitschakelen.
- **Schatten:** ontbrekende geboorte en overlijden afleiden uit doop en begrafenis, zodat de leeftijdsregels gelden.
- **Alle regels en grenzen op standaard** zet alles terug, **Opnieuw controleren** rekent opnieuw met de huidige instellingen.

## Werken met meldingen

Elke melding noemt de persoon, de regel en de betreffende gegevens.

- Een klik op de melding opent de persoon; **Gebeurtenis bewerken** wijzigt datum of plaats direct vanuit de melding (met bewerkingsrechten).
- **Afvinken als gecontroleerd:** meldingen die u hebt gecontroleerd en die zo blijven (bijv. een gedocumenteerde late geboorte), verdwijnen uit de lijst. **Afgevinkte tonen** haalt ze terug, **Opnieuw openen** verwijdert het vinkje.
- De afvinklijst wordt op deze pc bewaard, apart per server en stamboom. **Afgevinkte exporteren …** en **Afgevinkte importeren …** wisselen haar uit als bestand, bijv. met een mede-onderzoeker of voor een tweede computer.

## Afdrukken

**Afdrukken** en **Opslaan als PDF …** geven de huidige lijst met meldingen uit, met een samenvatting. De regel erboven zegt hoeveel personen en gezinnen zijn gecontroleerd en hoeveel meldingen fouten zijn.
