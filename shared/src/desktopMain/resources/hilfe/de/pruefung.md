# Plausibilitätsprüfung

**Extras › Plausibilitätsprüfung …** (Strg+Umschalt+P) oder das Symbol **Prüfung** prüft den ganzen Stammbaum, soweit du ihn sehen darfst, mit 77 Regeln. **Fehler** sind Widersprüche in den Daten (Tod vor Geburt, eigener Vorfahr), **Warnungen** sind ungewöhnlich, aber möglich (Mutter sehr jung, Pate schon gestorben, mögliche Dublette, Ortsvariante). Die Patenregeln (024, 126) nehmen mit api4webtrees ab 1.11 die verknüpften Paten und Trauzeugen direkt und suchen frei eingetragene über den Namen; mit einem älteren Modul lesen sie die Notiz „Paten: …“.

![](desktop-pruefung.jpg)


## Regeln und Grenzen

Die Regeln stehen links in Gruppen: **Chronologie**, **Altersgrenzen**, **Struktur**, **Namen**, **Quellen**, **Orte**, **Form**. Die Gruppe Form prüft den Aufbau einer GEDCOM-Datei (Verweise, Datumsform, Pflichtangaben); in wtWin greifen davon nur die Regeln, die auch im Baum sichtbar sind. Eine Datei mit allen Regeln, auch GEDCOM 7, prüft **Extras › GEDCOM-Datei prüfen …**. Ein Klick auf eine Regel zeigt nur ihre Treffer, **Alle Treffer** wieder alles.

- **Voreinstellung:** von streng bis großzügig; „Quellen belegen“ schaltet zusätzlich die Regeln 420 und 421 ein (Ereignisse und Personen ohne Quelle). Sobald du etwas änderst, heißt sie „Eigene Einstellung“.
- Je Regel: **Grenze** (Jahre oder Monate, die Vorgabe steht dabei), **Schwere** (Fehler oder Warnung) oder die Regel ganz ausschalten.
- **Schätzen:** fehlende Geburt und fehlenden Tod aus Taufe und Begräbnis ableiten, damit die Altersregeln greifen.
- **Alle Regeln und Grenzen wie vorgegeben** setzt zurück, **Neu prüfen** rechnet mit den aktuellen Einstellungen.

## Treffer bearbeiten

Jeder Treffer nennt Person, Regel und die betroffenen Angaben.

- Ein Klick auf den Treffer öffnet die Person; **Ereignis bearbeiten** ändert das Datum oder den Ort direkt aus dem Treffer (mit Bearbeitungsrecht).
- **Als geprüft abhaken:** Treffer, die du geprüft hast und die so bleiben (etwa eine belegte späte Geburt), verschwinden aus der Liste. **Abgehakte zeigen** holt sie zurück, **Wieder öffnen** streicht das Häkchen.
- Die Abhakliste liegt auf diesem PC, getrennt je Server und Stammbaum. **Abgehakte sichern …** und **Abgehakte laden …** tauschen sie als Datei aus, etwa mit einem Mitforscher oder für einen zweiten Rechner.

## Drucken

**Drucken** und **Als PDF speichern …** geben die aktuelle Trefferliste mit Zusammenfassung aus. Die Zeile darüber sagt, wie viele Personen und Familien geprüft wurden und wie viele Treffer davon Fehler sind.
