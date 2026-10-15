# gedaudit

**gedaudit** prüft eine GEDCOM-Datei auf Widersprüche und Auffälligkeiten, mit denselben Regeln wie die Plausibilitätsprüfung in wtWin, wtTux und wtMac. Es braucht kein webtrees und keinen Server: Die Datei wird nur gelesen, nichts wird verändert, nichts verlässt den Rechner.

## Datei öffnen

**Datei › GEDCOM-Datei öffnen …** (Strg+O) oder der Knopf auf der Startseite. Zuletzt geöffnete Dateien stehen auf der Startseite und unter **Datei › Zuletzt geöffnet**. Eine `.ged`-Datei lässt sich auch per Doppelklick im Dateimanager mit gedaudit öffnen.

Gelesen werden GEDCOM 5.5, 5.5.1 und 7. Der Zeichensatz kommt aus der Datei (UTF-8, UTF-16, ANSEL, ANSI); welcher erkannt wurde, steht in der Zeile über den Treffern. Wurde die Datei im anderen Programm geändert, holt **Datei neu laden** (F5) den neuen Stand.

## Prüfen

Links stehen die Regeln in Gruppen, rechts die Treffer. Wie die Regeln, Voreinstellungen, Grenzwerte und das Abhaken funktionieren, steht im Kapitel [Plausibilitätsprüfung](hilfe:pruefung). Der Unterschied zu wtWin: Es gibt kein Personenblatt und kein Bearbeiten, denn gedaudit schreibt nicht in die Datei. Korrigiert wird im Genealogieprogramm, aus dem die Datei stammt; danach die Datei neu exportieren und in gedaudit neu laden.

## Form der Datei

Die Gruppe **Form** gibt es nur für Dateien: Zeilen, die kein GEDCOM sind, Verweise auf Personen oder Familien, die es nicht gibt, doppelt vergebene Kennungen, Daten in unlesbarer Form, Personen ohne Namen, mehr als eine Geburt oder ein Tod, Familienverbindungen, die nur auf einer Seite stehen, ein fehlender Kopfsatz oder ein Zeichensatz im Kopf, der nicht zur Datei passt, und Tags außerhalb des Standards 5.5.1 oder 7. Die beiden letzten Regeln sind von Haus aus aus, weil sie bei manchen Programmen sehr viel melden.

**Ansicht › Aufbau der Datei …** (F6) zeigt den Tag-Baum je Datensatzart mit der Zahl der Vorkommen. Hersteller-Tags (mit Unterstrich) und Tags außerhalb des Standards sind farbig markiert. So siehst du, was ein Programm beim Export erzeugt und was ein anderes beim Import verlieren wird.

## Person ansehen

Ein Klick auf einen Treffer zeigt unten die **Person**: Ereignisse mit Datum, Ort und Zahl der Quellen, die Eltern, die Partner mit Heirat und die Kinder. Betrifft der Treffer zwei Personen, etwa eine mögliche Dublette oder Kind und Mutter, stehen beide nebeneinander. Die Kennungen (I123, F45) sind die aus der Datei, so findest du die Person im Herkunftsprogramm wieder. Die Trennlinie über der Karte lässt sich ziehen. **Strg+C** kopiert den Personentext der gewählten Person (Name, Kennung, Ereignisse, Eltern, Partner, Kinder), solange kein Eingabefeld den Fokus hat; ein Rechtsklick auf einen Treffer oder ein Ereignis bietet **Personentext kopieren** und **Zeile kopieren**, und die Werte der Karte lassen sich mit der Maus markieren. Der Rechtsklick bietet außerdem **In der CompGen-Meta-Suche suchen …**: Familienname und auf Wunsch ein Ort gehen an meta.genealogy.net, die Treffer aus GEDBAS, Online-Ortsfamilienbüchern und weiteren Datenbanken öffnen sich im Browser.

## Zwei Dateien vergleichen

**Datei › Mit anderer Datei vergleichen …** (Strg+D) stellt der geöffneten Datei eine zweite gegenüber, etwa einen älteren Stand, den Export aus einem anderen Programm oder die Datei eines Mitforschers. Personen werden über die Kennung zugeordnet, sonst über Name, Geburtsjahr und Geschlecht; nahe Treffer stehen unter „wahrscheinlich dieselbe Person“. Links die Gruppen: geändert, wahrscheinlich dieselbe, nur in der einen oder anderen Datei, unverändert. Ein Klick auf einen Eintrag zeigt die geänderten Felder und beide Personen nebeneinander. Verwandte werden über den Namen verglichen, weil Kennungen zwischen Dateien abweichen können. Drucken und PDF geben die Liste der Unterschiede aus. Es wird nichts zusammengeführt und nichts geschrieben.

## Abhaken

Treffer, die du geprüft hast und die so bleiben, hakst du ab; sie verschwinden aus der Liste und kommen erst wieder, wenn sich an der Stelle etwas ändert. Die Abhakliste liegt auf diesem PC, getrennt je Datei (nach ihrem Pfad). **Abgehakte sichern …** und **Abgehakte laden …** tauschen sie als Datei aus, auch mit wtWin.

## Drucken

**Datei › Drucken** (Strg+P) und **Als PDF speichern …** geben die aktuelle Trefferliste mit Zusammenfassung aus.

## Sprache und Aussehen

**Ansicht › Sprache** schaltet die Oberfläche um (Deutsch, Englisch, Französisch, Niederländisch, Spanisch), **Ansicht › Erscheinungsbild** zwischen hell und dunkel.

## GEDCOM-L

gedaudit kennt das GEDCOM-L-Addendum zu GEDCOM 5.5.1. Im Fenster **Aufbau der Datei** stehen seine Tags (_LOC, _GODP, _UID …) als „GEDCOM-L“ und nicht als Hersteller-Tag; die Kopfzeile nennt die Addendum-Version, wenn die Datei sie angibt. Die Personenkarte zeigt Paten und Zeugen ohne eigenen Datensatz (_GODP, _WITN), die Adresse sowie GOV-Kennung, Postleitzahl und Maidenhead-Locator am Ort. Verweise auf Ortsdatensätze (`3 _LOC` am Ort, `1 _LOC` im Ortsdatensatz), deren Ziel fehlt, meldet die Prüfung wie jeden anderen Verweis ins Leere.
