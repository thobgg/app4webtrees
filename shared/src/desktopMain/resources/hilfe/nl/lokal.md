# Stamboom op deze pc

Zonder server legt wtWin de stamboom op deze computer aan. Op de achtergrond draait een volledige webtrees met meegeleverde PHP, ongewijzigd ten opzichte van de officiële uitgave. Die is alleen op deze pc bereikbaar, start met het programma en stopt ermee. U ziet geen server en geen wachtwoord; het programma meldt zich zelf aan.

## Aanmaken

Bij de eerste start, rechts: voer een naam in en klik op **Stamboom aanmaken** (leeg) of **Uit een GEDCOM-bestand overnemen …**. Om over te stappen vanuit een ander programma exporteert u daar een GEDCOM-bestand (`.ged`) en kiest u het hier. Personen, gezinnen, gebeurtenissen, bronnen en notities worden overgenomen. Het GEDCOM-bestand zelf blijft ongewijzigd; een tweede import maakt een extra stamboom ernaast aan en overschrijft nooit.

Het bestand mag UTF-8 (met of zonder byte order mark), UTF-16, ANSEL of ANSI zijn; de tekenset wordt herkend en omgezet zoals bij de import in webtrees. Afzonderlijke onbruikbare records (zoals dubbele ID's) worden overgeslagen en in `import.log` vermeld, de rest wordt overgenomen.

Foto's komen niet mee uit de GEDCOM. Voeg ze toe in het programma, of kopieer ze later naar de mediamap (zie hieronder) en koppel ze in webtrees.

## Meerdere stambomen

**Bestand › Stambomen op deze pc …** toont alle stambomen met het aantal personen. Daar kunt u er een openen, hernoemen (titel wijzigen, dan het vinkje of Enter) of verwijderen (nooit de laatste), nog een lege aanmaken of nog een GEDCOM-bestand overnemen. Lege restanten van eerdere mislukte imports – alleen de voorbeeldpersoon „John Doe” van webtrees – ruimt het programma bij het starten zelf op; een stamboom die u zelf leeg hebt aangemaakt, blijft staan.

## Waar de gegevens staan

| Systeem | Map |
| - | - |
| Windows | `%LOCALAPPDATA%\app4webtrees` (in de adresbalk van de Verkenner typen) |
| Linux | `~/.local/share/app4webtrees` |

Daarin staan `webtrees/` met het programma en `webtrees/data/` met de database (SQLite) en de mediamap `media/`. Het logboek van de PHP-server is `php.log`. Wat wtWin zelf opmerkt (afgebroken verbindingen, herhalingen, herstarts van de server) staat in `wtwin.log` ernaast.

## Archief

De stamboom op deze pc wordt geleverd met de module **Sammlungen** (collecties): foto's en documenten staan als mappen onder `data/media`, hoeven niet aan personen te hangen en verschijnen onder **Foto's › Archief** en ook in webtrees in de browser. Kerkboekscans uit het archief kunnen in het bronnenbeheer als bron of bronvermelding worden toegewezen.

## Back-up

Het programma maakt niet automatisch een back-up. Twee manieren:

- Kopieer, terwijl wtWin gesloten is, de map `app4webtrees`, bijv. naar een USB-stick. Dat is de volledige back-up inclusief foto's.
- **webtrees in de browser openen** en de stamboom in het configuratiescherm als GEDCOM exporteren. Dat bewaart de gegevens, niet de afbeeldingen.

## Alles van webtrees

**Bestand › webtrees in de browser openen** toont uw webtrees in de browser: configuratiescherm, modules, namen wijzigen, bronnen aanmaken. De browser vraagt om een eigen aanmelding. De gebruikersnaam is uw aanmeldnaam op de pc; het wachtwoord is bij het aanmaken van de stamboom willekeurig gegenereerd en opgeslagen in het bestand `zugang.properties` in de map `app4webtrees` (zie hierboven). Open het bestand met een teksteditor en kopieer het wachtwoord. Geef het niet door; het is de beheerderstoegang tot uw stamboom.

## Verhuizen naar een NAS of webhoster

Wil de familie meelezen of werkt u op twee computers, dan verhuist de stamboom naar een server, bijv. een Synology met nas4webtrees. Daarna gebruikt u wtWin zoals voorheen, alleen verbonden.

1. In wtWin **webtrees in de browser openen**, daar **Configuratiescherm › Stamboom › Exporteren** als GEDCOM.
2. Foto's: kopieer de map `webtrees/data/media` (zie hierboven) naar `data/media` van de webtrees op de server.
3. Maak op de server een nieuwe stamboom aan en importeer de GEDCOM.
4. In wtWin **Bestand › Afmelden**, **Ander adres**, het serveradres invoeren. Of klik op de pagina **App** van de server op **Verbinden met wtWin**.

De stamboom op de pc blijft bewaard tot u de map verwijdert.

## Als er iets misgaat

Kan de stamboom niet worden aangemaakt, dan toont het programma een melding met het pad naar `php.log`. Meld het alstublieft op github.com/thobgg/app4webtrees/issues en voeg het bestand toe, en bij het importeren van een GEDCOM-bestand ook `import.log` uit dezelfde map (tekenset, overgeslagen records, reden van het mislukken). **Help › Over wtWin** toont of PHP en webtrees zijn gevonden.

**„Verbinding … onderbroken”:** valt de verbinding met de stamboom op deze pc weg terwijl webtrees in de browser werkt, dan filtert meestal een ander programma het netwerkverkeer van wtWin – advertentie- of webbescherming, ook de realtimebeveiliging van een virusscanner. Een uitzondering voor wtWin (`%LOCALAPPDATA%\Programs\wtWin\wtWin.exe`) in dat programma helpt meestal. Wat wtWin daarbij opmerkt, staat in `wtwin.log`.

**Startpersoon:** heeft een stamboom op deze pc nog geen startpersoon, dan vraagt het programma bij het openen één keer „Met wie moet de stamboom beginnen?” – persoon zoeken en aanklikken. De keuze wordt de standaardpersoon van de stamboom; wijzigen onder Persoon › Als startpersoon instellen …
