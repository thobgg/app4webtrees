# Listen

Das Fenster **Liste erstellen** öffnest du über **Erstellen › Ahnenliste**, **Stammliste**, **Ereignisliste** oder das Symbol **Liste**. Links die Listenart mit ihren Einstellungen, rechts die Vorschau der fertigen Seiten; unten **Drucken** und **Als PDF speichern …**.

## Vom Probanden aus

![](liste-ahnen.jpg)
![](liste-stamm.jpg)
![](liste-taufpaten.jpg)
![](liste-ahnenwertung.jpg)


- **Ahnenliste:** alle Vorfahren nach Generationen mit Kekulé-Nummern, bis 12 Generationen; wer mehrfach vorkommt, verweist auf seine erste Nummer (Ahnenschwund).
- **Spitzenahnen:** die Vorfahren, bei denen die Forschung endet, nach den vier Großeltern-Linien.
- **Stammlinie** und **Mutterstamm:** die Vater- bzw. Mutterlinie Generation für Generation, mit den Ehepartnern.
- **Ahnenwertung:** wie vollständig jede Generation erforscht ist, als Tabelle mit Ahnenschwund.
- **Stammliste:** alle Nachfahren mit Nummern, Ehepartnern und Heiraten, bis 10 Generationen. Nummerierung nach Saragossa, d’Aboville, Henry oder fortlaufend.
- **Nachfahren je Generation:** wie viele Nachfahren es in jeder Generation gibt: Männer, Frauen, Lebende, Namensträger.
- **Personenblatt:** alle Angaben zum Probanden auf einer Seite.

## Über den ganzen Stammbaum

![](liste-namen.jpg)
![](liste-orte.jpg)
![](liste-ereignisse.jpg)
![](liste-spitzenahnen.jpg)


- **Ereignisliste:** Geburten und Todesfälle aller sichtbaren Personen, zeitlich geordnet; wahlweise als **Jahrestagskalender** nach Monat und Tag, mit Ortsfilter, etwa für die Familienzeitung.
- **Namensliste:** alle Familiennamen mit Anzahl, Zeitraum und den häufigsten Orten.
- **Ortsliste:** alle Orte, wie sie an den Ereignissen stehen – **nur Orte** (Ereignisse, Personen, Zeitraum), **mit Familiennamen** oder **mit Personen** und ihren Ereignissen dort. **Ortsdetails** fügt GOV-Kennung und Koordinaten aus der Ortsverwaltung hinzu, **Notizen** und **Quellen** die vom Ortsdatensatz.
- **Familienliste:** alle Ehen mit Heirat und Kinderzahl, alphabetisch oder chronologisch.
- **Berufe und Konfessionen:** wer welchen Beruf hatte bzw. welcher Konfession angehörte.
- **Belegt durch Quellen:** wie gut der Stammbaum belegt ist – Personen und Ereignisse mit Quelle in Prozent, Fotos, je Ereignisart, und wer noch ganz ohne Quelle ist. Der Vergleichswert unter Forschern.
- **Taufpaten:** alle Taufen mit ihren Paten, zeitlich geordnet; mit api4webtrees ab 1.11 aus den verknüpften und den frei eingetragenen Paten („verlinkt: 2 von 3“ zeigt, wie viele einen eigenen Datensatz haben), sonst aus der Notiz „Paten: …“.

Listen über den ganzen Stammbaum brauchen den Baum am Stück; dafür muss auf dem Server api4webtrees ab Version 1.9 laufen. Beim Stammbaum auf diesem PC ist das immer der Fall.

Die Vorschau zeigt die ersten Seiten; sind es mehr, steht darunter „… und n weitere Seiten im PDF“. Jede Seite trägt unten „Erstellt mit wtWin am …“.

## DNA-Vererbungslinien

**Erstellen › DNA-Vererbungslinien …** zeigt für den Probanden, von welchen Vorfahren er Y-DNA, mitochondriale DNA (mtDNA) oder X-DNA haben kann und welche Nachkommen sie von ihm haben können. Oben wählst du Linie, Richtung und die Zahl der Generationen.

- **Y-DNA:** vom Vater an den Sohn. Bei einer Frau steht die Linie ihres Vaters, mit dem Hinweis, dass sie selbst kein Y-Chromosom hat.
- **mtDNA:** von der Mutter an alle Kinder; nur Töchter geben sie weiter.
- **X-DNA:** ein Mann hat sein X von der Mutter und gibt es an die Töchter, eine Frau hat je eines von beiden Eltern und gibt X an alle Kinder. Je Generation steht, wie viele der möglichen X-Vorfahren im Stammbaum stehen.

Vorfahren stehen nach Generation mit ihrer Kekulé-Nummer, Nachkommen als eingerückte Liste – das sind die Personen, die für die Linie einen Test machen könnten. Ein Klick macht die Person im Hauptfenster zum Probanden; **Von … ausgehen** berechnet die Linien für sie neu.

Die Linien sind aus den eingetragenen Verwandtschaften abgeleitet und kein Testergebnis; bei X-DNA heißt „möglich“ nicht, dass tatsächlich X-DNA geteilt wird. Es zählen nur leibliche Eltern: Bei einem Adoptiv- oder Pflegekind und bei einer Person ohne eingetragenes Geschlecht endet die Linie mit einem Vermerk. Der Menüpunkt erscheint nur, wenn auf dem Server das webtrees-Modul dna4webtrees installiert ist; es berechnet die Linien.
