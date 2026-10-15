# gedaudit

**gedaudit** controleert een GEDCOM-bestand op tegenstrijdigheden en opvallendheden, met dezelfde regels als de plausibiliteitscontrole in wtWin, wtTux en wtMac. Het heeft geen webtrees en geen server nodig: het bestand wordt alleen gelezen, er wordt niets gewijzigd en niets verlaat de computer.

## Bestand openen

**Bestand › GEDCOM-bestand openen …** (Ctrl+O) of de knop op de startpagina. Onlangs geopende bestanden staan op de startpagina en onder **Bestand › Onlangs geopend**. Een `.ged`-bestand kan ook met een dubbelklik in de bestandsbeheerder met gedaudit worden geopend.

Gelezen worden GEDCOM 5.5, 5.5.1 en 7. De tekenset komt uit het bestand (UTF-8, UTF-16, ANSEL, ANSI); welke herkend is, staat in de regel boven de bevindingen. Is het bestand in het andere programma gewijzigd, dan haalt **Bestand opnieuw laden** (F5) de nieuwe stand op.

## Controleren

Links staan de regels in groepen, rechts de bevindingen. Hoe regels, voorinstellingen, grenzen en afvinken werken, staat in het hoofdstuk [Plausibiliteitscontrole](hilfe:pruefung). Het verschil met wtWin: er is geen persoonsblad en geen bewerken, want gedaudit schrijft niet in het bestand. Corrigeren doet u in het genealogieprogramma waar het bestand vandaan komt; daarna het bestand opnieuw exporteren en in gedaudit opnieuw laden.

## Vorm van het bestand

De groep **Vorm** bestaat alleen voor bestanden: regels die geen GEDCOM zijn, koppelingen naar personen of gezinnen die niet bestaan, dubbel gebruikte kenmerken, data in een onleesbare vorm, personen zonder naam, meer dan één geboorte of overlijden, gezinskoppelingen die maar aan één kant staan, een ontbrekende kop of een tekenset in de kop die niet bij het bestand past, en labels buiten de standaard 5.5.1 of 7. De laatste twee regels staan standaard uit, omdat ze bij sommige programma's heel veel melden.

**Beeld › Opbouw van het bestand …** (F6) toont de labelboom per recordtype met het aantal keren dat elk label voorkomt. Leverancierslabels (met onderstrepingsteken) en labels buiten de standaard zijn gekleurd gemarkeerd. Zo ziet u wat een programma bij het exporteren maakt en wat een ander bij het importeren zal verliezen.

## Twee bestanden vergelijken

**Bestand › Met een ander bestand vergelijken …** (Ctrl+D) zet een tweede bestand naast het geopende, bijvoorbeeld een oudere stand, de export uit een ander programma of het bestand van een medeonderzoeker. Personen worden gekoppeld op kenmerk, anders op naam, geboortejaar en geslacht; bijna-treffers staan onder „waarschijnlijk dezelfde persoon“. Links de groepen: gewijzigd, waarschijnlijk dezelfde, alleen in het ene of het andere bestand, ongewijzigd. Een klik op een regel toont de gewijzigde velden en beide personen naast elkaar. Verwanten worden op naam vergeleken, omdat kenmerken tussen bestanden kunnen verschillen. Afdrukken en PDF geven de lijst met verschillen. Er wordt niets samengevoegd en niets geschreven.

## Persoon bekijken

Een klik op een bevinding toont onderaan de **persoon**: gebeurtenissen met datum, plaats en aantal bronnen, de ouders, de partners met huwelijk en de kinderen. Betreft de bevinding twee personen, zoals een mogelijke dubbele of kind en moeder, dan staan beide naast elkaar. De kenmerken (I123, F45) zijn die uit het bestand, zo vindt u de persoon in het oorspronkelijke programma terug. De scheidslijn boven de kaart kan worden versleept. **Ctrl+C** kopieert de persoonstekst van de gekozen persoon (naam, kenmerk, gebeurtenissen, ouders, partners, kinderen) zolang geen invoerveld de focus heeft; een rechtsklik op een bevinding of een gebeurtenis biedt **Persoonstekst kopiëren** en **Regel kopiëren**, en de waarden van de kaart kunnen met de muis worden geselecteerd. De rechtsklik biedt ook **Zoeken in de CompGen-metazoekmachine …**: de achternaam en desgewenst een plaats gaan naar meta.genealogy.net, en de treffers uit GEDBAS, online dorpsfamilieboeken en andere databanken openen in de browser.

## Afvinken

Bevindingen die u bekeken hebt en die zo blijven, vinkt u af; ze verdwijnen uit de lijst en komen pas terug als er op die plek iets verandert. De afvinklijst staat op deze pc, apart per bestand (op pad). **Afgevinkte opslaan …** en **Afgevinkte laden …** wisselen ze uit als bestand, ook met wtWin.

## Afdrukken

**Bestand › Afdrukken** (Ctrl+P) en **Opslaan als PDF …** geven de huidige lijst met bevindingen met een samenvatting.

## Taal en weergave

**Beeld › Taal** schakelt de interface om (Duits, Engels, Frans, Nederlands, Spaans), **Beeld › Weergave** tussen licht en donker.

## GEDCOM-L

gedaudit kent het GEDCOM-L-addendum bij GEDCOM 5.5.1. In het venster **Opbouw van het bestand** staan de labels ervan (_LOC, _GODP, _UID …) als „GEDCOM-L” en niet als leverancierslabel; de kopregel noemt de addendumversie als het bestand die vermeldt. De persoonskaart toont peetouders en getuigen zonder eigen record (_GODP, _WITN), het adres en de GOV-code, postcode en Maidenhead-locator van de plaats. Verwijzingen naar plaatsrecords (`3 _LOC` bij een plaats, `1 _LOC` in een plaatsrecord) waarvan het doel ontbreekt, meldt de controle zoals elke andere verwijzing in het niets.
