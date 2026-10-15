# Bücher

**Erstellen › Buch erstellen …** macht aus dem Stammbaum ein Buch im Stil gedruckter Ortsfamilienbücher: jede Person mit Ereignissen, Quellen, Paten und Notizen, Verweise auf Eltern und Kinder, Porträts am Rand, Inhaltsverzeichnis und Register.

## Drei Bücher

![](buch-vor-titel.jpg)
![](buch-vor-gen.jpg)
![](buch-nach-gen.jpg)
![](buch-fam.jpg)


- **Vorfahrenbuch:** alle Vorfahren des Probanden nach Generationen und Kekulé-Nummern, mit Lebensdaten, Taufen, Begräbnissen, Quellen und Notizen. Die vier Großeltern-Linien auf Wunsch farbig am Rand.
- **Nachfahrenbuch:** alle Nachfahren Generation für Generation, mit Ehepartnern, Kindern und Verweisen; Nummern nach Saragossa, d’Aboville, Henry oder fortlaufend; Zweigfarben je Kind der Stammeltern.
- **Familienbuch:** ein Eintrag je Familie, alphabetisch oder chronologisch. Mit **Aufbau „je Person“** wird daraus der Personenbericht: ein Eintrag je Person mit Eltern, Ehen und Kindern als Verweise, Verzeichnisse auf Personennummern. Mit **Ortsfilter** wird daraus ein Ortsfamilienbuch. Braucht den ganzen Stammbaum am Stück (api4webtrees ab 1.9 auf dem Server). Mit **Häuser und Höfe** (ab api4webtrees 1.15) kommt ein Häuserteil dazu: alle Höfe und Häuser des Orts aus der Ortsverwaltung (Ortsdatensätze mit Art unter dem Ort) mit ihrer Geschichte und den Bewohnern und Besitzern der Zeit nach, je mit Verweis auf die Familie; die Familien verweisen zurück auf ihr Haus (H1, H2 …). Was ein Haus ist, entscheidet die Art im Ortsdatensatz (Haus, Hof, Mühle, Kirche …; ohne Art zählt eine Hausnummer im Namen); Stadtteile und Dörfer gliedern als Kapitel, Orte mit Bewohnern, die kein Gebäude sind, stehen im Anhang „Weitere Orte“. Trägt der Ortsdatensatz eine GOV-Typnummer (GEDCOM-L `2 _GOVTYPE`, api4webtrees ab 1.18.1), entscheidet sie vor dem Text: Hof, Gebäude, Mühle, Gut, Kirche … sind Häuser, Dorf, Stadtteil, Gemeinde … Ebenen darüber. Die Haken „Nur Häuser und Höfe“ und „Orte ohne Art aufnehmen“ steuern das. Ortsnamen dürfen mit Komma oder Semikolon gegliedert sein.

## Einstellungen

- **Daten:** Generationen (2 bis 12), Notizen, Quellen, Ortsnamen kürzen, doppelte Vorfahren ganz darstellen (statt „siehe Nr.“).
- **Lebende:** fürs Veröffentlichen erscheinen Lebende vollständig, nur mit Namen oder gar nicht; als lebend gilt, wen webtrees für lebend hält oder wer jünger als das Grenzalter ist (80 bis 120 Jahre). Nur im Familienbuch und Personenbericht.
- **Vorwort aus Datei …** liest eine Textdatei ins Vorwort; **Vorlage speichern …/laden …** sichert Buchart und alle Einstellungen als `.wtbuch`-Datei, etwa je Ort.
- **Darstellung:** Bilder, Farbkodierung, Vorwort (eigener Text auf der ersten Seite), Tafel als Ausklappseite (A3, nur im PDF).
- **Verzeichnisse:** Namen, Orte, Berufe, Quellen, jeweils auf die Eintragsnummern.

## Speichern

![](buch-vor-reg.jpg)
![](buch-nach-titel.jpg)


**Buch speichern** fragt nach dem Format:

- **PDF** mit Lesezeichen und Links (Klick auf „siehe Nr.“ springt zum Eintrag).
- **DOCX** zum Weiterbearbeiten in Word oder LibreOffice. Das Inhaltsverzeichnis dort einmal aktualisieren: anklicken und F9 (LibreOffice: Extras › Verzeichnisse › Aktualisieren).
- **Website:** ein Ordner zum Hochladen – Startseite mit Vorwort und Kacheln, alle Einträge auf einer Seite mit Buchstabenleiste, jeder Eintrag zusätzlich als eigene Seite mit Blättern, Häuser, Verzeichnisse als Seiten, Stylesheet und Bilder als Dateien. Lebende vorher auf „nur mit Namen“ stellen.
- **HTML** für die Homepage, **TeX** für den Satz mit LaTeX, **Text**.

Bei großen Stammbäumen dauert das Laden der Personen und Bilder einen Moment; das Fenster zeigt den Fortschritt.

## Große Stammbäume mit vielen Bildern

webtrees rechnet jedes Vorschaubild beim ersten Abruf auf dem Server neu – bei Tausenden Personen dauert der erste Lauf entsprechend. wtWin lädt je Person nur das Hauptbild, sechs gleichzeitig, zeigt den Fortschritt („Bilder 2 340/8 900 – von diesem PC …, Zwischenspeicher …, Server …“) und lässt sich mit **Ohne die restlichen Bilder fortfahren** abbrechen. Was einmal geladen ist, bleibt im Zwischenspeicher auf diesem PC (`~/.cache/app4webtrees/medien/<server>/<stammbaum>/`, unter Windows im Anwendungsdaten-Ordner); der nächste Lauf und die Tafeln danach brauchen nichts mehr vom Server. Im Buchdialog unter Darstellung stehen die Größe des Zwischenspeichers und **Bilder-Zwischenspeicher leeren**.

Wer eine Kopie des webtrees-Medienordners auf diesem PC hat (etwa weil er per FTP hochlädt), trägt sie als **Medienordner auf diesem PC** ein: Bilder werden dann von dort gelesen und selbst verkleinert, ohne Server. Alle anderen lassen das Feld leer. webtrees bleibt immer die Quelle; der Zwischenspeicher und der Ordner sind nur Kopien.

**Sichtbarkeit:** Hier wählst du, für wen das Buch ist: alles (Sicht des Verwalters), was Mitglieder sehen dürfen, oder was Besucher sehen dürfen. Das Buch lässt dann weg, was webtrees diesem Leser verbirgt – Ereignisse mit Einschränkung („Für Mitglieder“, „Für Verwalter“), Angaben nach den Datenschutzregeln des Stammbaums, Paten und Familien, die er nicht sehen darf, und Lebensdaten lebender Personen; deren Namen bleiben nur, wenn der Stammbaum Namen Lebender diesem Leser zeigt, sonst steht „Privat“. Mitglieder sehen also mehr als Besucher, aber nichts, was nur Verwaltern gezeigt wird. So lässt sich ein Buch weitergeben, auch wenn ein Verwalter es erzeugt. Die Website enthält immer nur, was Besucher sehen dürfen. Braucht api4webtrees ab 1.21.
