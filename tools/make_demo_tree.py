#!/usr/bin/env python3
"""
Erzeugt den Demo-Stammbaum "Familie Falkenrath": demo-tree/falkenrath.ged + demo-tree/media/.

Alle Personen, Lebensdaten und Dokumente sind FREI ERFUNDEN; die Orte gibt es wirklich (fuer die Karte).
Bilder werden hier gezeichnet (Portraits im Stil alter Atelierfotos, nachgebaute Urkunden) - nichts
stammt aus fremden Quellen. Das Ergebnis steht unter CC0 und darf ueberall verwendet werden
(README, Screenshots, Store, Demo im Modul-Repo).

Der Baum deckt ab, was App und Modul koennen muessen: lebende Personen (Datenschutz), zwei Ehen,
frueh verstorbenes Kind, Gefallene, zwei Auswanderungen nach Milwaukee (1888 und 1952), Scheidung,
unbekannte Mutter, Vettern und Cousinen auf beiden Seiten, Quellen, Notizen, Orte mit Koordinaten,
Medien an Personen und Familien. Rund 100 Personen, damit Listen und Baumansichten etwas zu zeigen haben.

Aufruf:  python3 tools/make_demo_tree.py
"""

import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'demo-tree'
MEDIA = OUT / 'media'

# ── Orte (Name, Breite, Laenge) ──────────────────────────────────────
PLACES = {
    'Celle': ('Celle, Niedersachsen, Deutschland', 52.6226, 10.0815),
    'Eschede': ('Eschede, Celle, Niedersachsen, Deutschland', 52.7347, 10.2353),
    'Uelzen': ('Uelzen, Niedersachsen, Deutschland', 52.9657, 10.5611),
    'Lueneburg': ('Lüneburg, Niedersachsen, Deutschland', 53.2509, 10.4141),
    'Hannover': ('Hannover, Niedersachsen, Deutschland', 52.3759, 9.7320),
    'Bremen': ('Bremen, Deutschland', 53.0793, 8.8017),
    'Milwaukee': ('Milwaukee, Wisconsin, USA', 43.0389, -87.9065),
    'Flandern': ('Langemark, Westflandern, Belgien', 50.9130, 2.9200),
    'Orel': ('Orjol, Russland', 52.9651, 36.0785),
    'Cambrai': ('Cambrai, Nord, Frankreich', 50.1758, 3.2346),
    'Bremerhaven': ('Bremerhaven, Deutschland', 53.5396, 8.5809),
    'Hermannsburg': ('Hermannsburg, Celle, Niedersachsen, Deutschland', 52.8333, 10.0833),
}

# ── Quellen ──────────────────────────────────────────────────────────
SOURCES = {
    'S1': ('Kirchenbuch Eschede, Taufen und Trauungen 1760–1840', 'Ev.-luth. Kirchengemeinde Eschede', 'Abschrift im Familienbesitz (erfunden für den Demo-Stammbaum)'),
    'S2': ('Standesamt Celle, Heiratsregister', 'Stadt Celle', ''),
    'S3': ('Familienbibel Falkenrath', 'Familie Falkenrath', 'Handschriftliche Einträge auf den Vorsatzblättern, begonnen 1862.'),
    'S4': ('Deutsche Verlustlisten 1914–1919', 'Preußisches Kriegsministerium', 'Ausgabe vom 4. November 1918 (Angabe erfunden).'),
}


def P(xref, given, surname, sex, birth=None, death=None, occu=None, facts=(), note=None, portrait=None, call=None):
    return dict(xref=xref, given=given, surname=surname, sex=sex, birth=birth, death=death, occu=occu, facts=list(facts), note=note, portrait=portrait, call=call)


# birth/death: (GEDCOM-Datum, Ortsschluessel) - death=None heisst: lebt (bei Geburt nach 1930) bzw. unbekannt
PEOPLE = [
    # Generation 0 und Kinder - leben
    P('I1', 'Jonas', 'Falkenrath', 'M', ('14 MAR 1985', 'Hannover'), occu='Bauingenieur', facts=[('EDUC', 'Leibniz Universität Hannover, Diplom 2010', None, 'Hannover')]),
    P('I2', 'Mira', 'Sandvoss', 'F', ('2 SEP 1987', 'Bremen'), occu='Buchhändlerin'),
    P('I3', 'Emil', 'Falkenrath', 'M', ('9 MAY 2017', 'Hannover')),
    P('I4', 'Ida', 'Falkenrath', 'F', ('23 NOV 2020', 'Hannover')),
    P('I5', 'Lena', 'Falkenrath', 'F', ('30 JUL 1988', 'Hannover'), occu='Ärztin'),
    P('I6', 'Tomasz', 'Wolniak', 'M', ('11 JAN 1986', 'Bremen'), occu='Informatiker'),
    P('I7', 'Nele', 'Wolniak', 'F', ('4 APR 2019', 'Hannover')),
    P('I90', 'Katja', 'Neumann', 'F', ('15 FEB 1986', 'Celle'), occu='Erzieherin'),
    P('I91', 'Leon', 'Marwede', 'M', ('28 AUG 2014', 'Celle')),
    P('I88', 'Florian', 'Ahlers', 'M', ('12 MAR 1990', 'Lueneburg'), occu='Zimmerer'),
    P('I89', 'Julia', 'Ahlers', 'F', ('7 JUL 1993', 'Lueneburg'), occu='Studentin'),
    P('I93', 'Michael', 'Falkenrath', 'M', ('3 MAY 1988', 'Milwaukee'), occu='Teacher'),
    P('I94', 'Emily', 'Falkenrath', 'F', ('19 OCT 1991', 'Milwaukee')),
    # Generation 1 - leben (bis auf Holger)
    P('I8', 'Bernd', 'Falkenrath', 'M', ('3 NOV 1955', 'Celle'), occu='Lehrer'),
    P('I9', 'Karin', 'Ilgner', 'F', ('21 JAN 1958', 'Lueneburg'), occu='Bibliothekarin'),
    P('I10', 'Ute', 'Falkenrath', 'F', ('16 AUG 1959', 'Celle'), occu='Krankenschwester'),
    P('I11', 'Rolf', 'Marwede', 'M', ('27 FEB 1957', 'Uelzen'), occu='Kfz-Meister'),
    P('I12', 'Sven', 'Marwede', 'M', ('8 OCT 1984', 'Celle')),
    P('I13', 'Anke', 'Marwede', 'F', ('19 JUN 1987', 'Celle')),
    P('I14', 'Holger', 'Ilgner', 'M', ('5 DEC 1961', 'Lueneburg'), ('28 MAR 2019', 'Lueneburg'), occu='Schlosser', note='Blieb unverheiratet und lebte bis zuletzt im Elternhaus an der Ilmenau.'),
    P('I86', 'Renate', 'Ilgner', 'F', ('9 APR 1963', 'Lueneburg'), occu='Steuerfachangestellte'),
    P('I87', 'Dirk', 'Ahlers', 'M', ('22 OCT 1960', 'Lueneburg'), occu='Dachdeckermeister'),
    P('I92', 'Susan', 'Miller', 'F', ('30 JAN 1960', 'Milwaukee'), occu='Nurse'),
    P('I75', 'Petra', 'Behrens', 'F', ('14 JUN 1958', 'Celle'), occu='Floristin'),
    P('I97', 'Andrea', 'Bock', 'F', ('2 MAR 1962', 'Lueneburg'), occu='Konditorin'),
    # Generation 2
    P('I15', 'Wilhelm', 'Falkenrath', 'M', ('8 FEB 1928', 'Celle'), ('17 OCT 2003', 'Celle'), occu='Tischlermeister', portrait='m-1955',
      facts=[('CONF', '', '5 APR 1942', 'Celle'), ('RESI', 'Werkstatt und Wohnhaus Am Heiligen Kreuz', 'FROM 1956 TO 2003', 'Celle')],
      note='Übernahm 1956 die Tischlerei seines Vaters.\nIn der Familie erzählt man, er habe jede Schublade im Haus selbst gebaut.'),
    P('I16', 'Hannelore', 'Oberlin', 'F', ('30 JUN 1931', 'Uelzen'), ('4 APR 2014', 'Celle'), occu='Schneiderin', portrait='f-1955'),
    P('I17', 'Gerda', 'Thielbar', 'F', ('12 MAY 1929', 'Celle'), ('2 MAR 1952', 'Celle'), note='Starb wenige Stunden nach der Geburt ihres Sohnes Dieter.', portrait='f-1950'),
    P('I18', 'Dieter', 'Falkenrath', 'M', ('2 MAR 1952', 'Celle'), ('19 MAR 1952', 'Celle')),
    P('I19', 'Friedrich', 'Ilgner', 'M', ('19 SEP 1926', 'Lueneburg'), ('7 JUN 1998', 'Lueneburg'), occu='Schlosser', portrait='m-1960', call='Fritz'),
    P('I20', 'Margarete', 'Krümmel', 'F', ('11 NOV 1930', 'Lueneburg'), ('22 AUG 2011', 'Lueneburg'), occu='Verkäuferin', portrait='f-1960'),
    P('I82', 'Irmgard', 'Ilgner', 'F', ('17 JUL 1929', 'Lueneburg'), ('3 FEB 2016', 'Lueneburg'), occu='Hausfrau', portrait='f-1950'),
    P('I83', 'Horst', 'Peters', 'M', ('25 NOV 1926', 'Lueneburg'), ('12 SEP 1999', 'Lueneburg'), occu='Zollbeamter'),
    P('I84', 'Jürgen', 'Peters', 'M', ('6 OCT 1957', 'Lueneburg'), occu='Polizist'),
    P('I95', 'Ilse', 'Krümmel', 'F', ('23 SEP 1936', 'Lueneburg'), occu='Bäckereifachverkäuferin'),
    P('I96', 'Manfred', 'Bock', 'M', ('1 APR 1933', 'Lueneburg'), ('15 NOV 2004', 'Lueneburg'), occu='Konditormeister',
      note='Führte die Bäckerei Krümmel nach dem Tod des Schwiegervaters unter eigenem Namen weiter.'),
    P('I73', 'Hildegard', 'Behrens', 'F', ('20 MAR 1928', 'Celle'), ('8 DEC 2015', 'Celle'), occu='Buchhalterin'),
    P('I74', 'Günter', 'Behrens', 'M', ('11 AUG 1931', 'Celle'), ('27 JAN 2009', 'Celle'), occu='Elektriker'),
    P('I85', 'Ingrid', 'Kruse', 'F', ('4 MAY 1934', 'Celle'), occu='Verkäuferin'),
    P('I77', 'Gertrud', 'Timmermann', 'F', ('2 FEB 1920', 'Hannover'), ('18 OCT 2001', 'Hannover'), occu='Fernmeldegehilfin'),
    P('I78', 'Hans', 'Timmermann', 'M', ('9 JUN 1923', 'Hannover'), ('5 MAR 1990', 'Hannover'), occu='Kaufmann'),
    P('I81', 'Herbert', 'Rosskamp', 'M', ('30 JUL 1926', 'Celle'), ('14 APR 2001', 'Celle'), occu='Gastwirt', portrait='m-1955'),
    P('I100', 'Hans', 'Oberlin', 'M', ('16 DEC 1925', 'Uelzen'), ('9 SEP 1998', 'Uelzen'), occu='Landwirt'),
    P('I69', 'Helen', 'Falkenrath', 'F', ('12 AUG 1920', 'Milwaukee'), ('6 JAN 2005', 'Milwaukee'), occu='Teacher', portrait='f-1945'),
    # Generation 3
    P('I21', 'Heinrich', 'Falkenrath', 'M', ('26 APR 1897', 'Celle'), ('9 JAN 1969', 'Celle'), occu='Tischler', portrait='m-1925',
      facts=[('EVEN', 'Kriegsdienst im Infanterie-Regiment 77', 'FROM 1916 TO 1918', 'Flandern', 'Militärdienst')]),
    P('I22', 'Anna', 'Rosskamp', 'F', ('13 AUG 1901', 'Celle'), ('30 NOV 1985', 'Celle'), portrait='f-1925'),
    P('I23', 'Elfriede', 'Falkenrath', 'F', ('6 JAN 1925', 'Celle'), ('14 FEB 2010', 'Celle'), occu='Kontoristin', portrait='f-1948'),
    P('I24', 'Kurt', 'Sandmann', 'M', ('17 OCT 1921', 'Hannover'), ('12 JUL 1943', 'Orel'), occu='Schriftsetzer', note='Gefallen bei Orjol. Die Ehe blieb kinderlos.'),
    P('I25', 'Otto', 'Falkenrath', 'M', ('22 MAR 1930', 'Celle'), ('3 SEP 2008', 'Milwaukee'), occu='Werkzeugmacher', portrait='m-1955b',
      facts=[('EMIG', '', '14 APR 1952', 'Bremen'), ('IMMI', 'Ankunft mit der MS „Gripsholm“', '25 APR 1952', 'Milwaukee'), ('NATU', '', '1958', 'Milwaukee')],
      note='Wanderte 1952 nach Amerika aus und kam zunächst bei Walter Falkenrath unter, dem Vetter seines Vaters,\ndessen Familie seit 1888 in Milwaukee lebte.'),
    P('I26', 'Dorothy', 'Keller', 'F', ('9 JUN 1933', 'Milwaukee'), ('1 DEC 2015', 'Milwaukee')),
    P('I27', 'Robert', 'Falkenrath', 'M', ('15 SEP 1958', 'Milwaukee'), occu='Engineer'),
    P('I28', 'Georg', 'Oberlin', 'M', ('2 OCT 1899', 'Uelzen'), ('25 MAY 1978', 'Uelzen'), occu='Landwirt', portrait='m-1930'),
    P('I29', 'Frieda', 'Behnke', 'F', ('18 FEB 1904', 'Uelzen'), ('6 SEP 1990', 'Uelzen'), portrait='f-1930'),
    P('I30', 'Hermann', 'Ilgner', 'M', ('7 JUL 1895', 'Lueneburg'), ('13 DEC 1961', 'Lueneburg'), occu='Eisenbahner'),
    P('I31', 'Elise', 'Marquardt', 'F', ('29 MAR 1898', 'Lueneburg'), ('10 OCT 1972', 'Lueneburg')),
    P('I32', 'Paul', 'Krümmel', 'M', ('1 MAY 1902', 'Lueneburg'), ('16 JAN 1980', 'Lueneburg'), occu='Bäckermeister', portrait='m-1935'),
    P('I33', 'Berta', 'Hagedorn', 'F', ('24 DEC 1905', 'Lueneburg'), ('8 JUL 1993', 'Lueneburg')),
    P('I34', 'Heinz', 'Krümmel', 'M', ('3 MAR 1933', 'Lueneburg'), ('18 APR 1945', 'Lueneburg'), note='Kam bei einem Luftangriff in den letzten Kriegstagen ums Leben.'),
    P('I35', 'Mathilde', 'Oberlin', 'F', ('20 JAN 1929', 'Uelzen'), ('2 NOV 2020', 'Uelzen')),
    P('I36', 'Werner', 'Kass', 'M', ('6 AUG 1925', 'Uelzen'), ('27 APR 1990', 'Uelzen'), occu='Landmaschinenhändler'),
    P('I37', 'Birgit', 'Kass', 'F', ('12 DEC 1955', 'Uelzen')),
    P('I38', 'Pauline', 'Rosskamp', 'F', ('5 SEP 1903', 'Celle'), ('21 JUN 1999', 'Celle'), occu='Lehrerin', note='Unverheiratet. Führte über Jahrzehnte die Familienchronik, aus der viele Angaben dieses Stammbaums stammen.'),
    P('I70', 'Wilhelm', 'Falkenrath', 'M', ('3 JUL 1899', 'Celle'), ('27 OCT 1918', 'Cambrai'), occu='Zimmermannslehrling', portrait='m-1917',
      facts=[('EVEN', 'Reserve-Infanterie-Regiment 78', 'FROM 1917 TO 1918', 'Cambrai', 'Militärdienst')],
      note='Gefallen zwei Wochen vor dem Waffenstillstand. Sein Bruder Heinrich hat die Nachricht nie verwunden.'),
    P('I71', 'Martha', 'Falkenrath', 'F', ('14 FEB 1902', 'Celle'), ('30 SEP 1988', 'Celle'), occu='Näherin', portrait='f-1926'),
    P('I72', 'Karl', 'Behrens', 'M', ('8 MAY 1898', 'Celle'), ('21 MAR 1970', 'Celle'), occu='Postschaffner'),
    P('I76', 'Albert', 'Timmermann', 'M', ('25 JAN 1890', 'Hannover'), ('17 AUG 1962', 'Hannover'), occu='Postbeamter'),
    P('I79', 'Ludwig', 'Rosskamp', 'M', ('11 MAR 1898', 'Celle'), ('2 JUN 1975', 'Celle'), occu='Gastwirt', portrait='m-1930',
      note='Übernahm 1931 das Gasthaus „Zum Allerkrug“ seines Vaters.'),
    P('I80', 'Else', 'Reinecke', 'F', ('19 OCT 1903', 'Hermannsburg'), ('7 JUL 1989', 'Celle')),
    P('I98', 'Heinrich', 'Oberlin', 'M', ('4 FEB 1895', 'Uelzen'), ('28 NOV 1968', 'Uelzen'), occu='Hofbesitzer', portrait='m-1925b'),
    P('I99', 'Minna', 'Lüdemann', 'F', ('22 JUN 1899', 'Uelzen'), ('13 MAR 1981', 'Uelzen')),
    P('I67', 'Walter', 'Falkenrath', 'M', ('5 SEP 1892', 'Milwaukee'), ('20 FEB 1965', 'Milwaukee'), occu='Machinist', portrait='m-1920'),
    P('I68', 'Ernest', 'Falkenrath', 'M', ('27 NOV 1895', 'Milwaukee'), ('3 APR 1970', 'Milwaukee'), occu='Brewery worker', note='In der Familie „Ernie“. Blieb unverheiratet.'),
    P('I103', 'Ruth', 'Lindgren', 'F', ('8 MAR 1896', 'Milwaukee'), ('30 NOV 1978', 'Milwaukee')),
    P('I64', 'Emma', 'Bartels', 'F', ('16 APR 1890', 'Celle'), ('1 JAN 1970', 'Celle'), occu='Hebamme',
      note='Unverheiratet. Holte, so die Familienchronik, „halb Celle auf die Welt“.'),
    # Generation 4
    P('I39', 'Carl', 'Falkenrath', 'M', ('19 NOV 1866', 'Celle'), ('4 MAR 1938', 'Celle'), occu='Zimmermann', portrait='m-1900'),
    P('I40', 'Dorothea', 'Wichmann', 'F', ('8 JUN 1870', 'Celle'), ('15 DEC 1949', 'Celle'), portrait='f-1900'),
    P('I41', 'Marie', 'Falkenrath', 'F', ('27 SEP 1894', 'Celle'), ('3 MAY 1975', 'Hannover')),
    P('I42', 'August', 'Rosskamp', 'M', ('30 JAN 1868', 'Celle'), ('12 AUG 1931', 'Celle'), occu='Gastwirt'),
    P('I43', 'Sophie', 'Lüders', 'F', ('14 APR 1872', 'Eschede'), ('9 FEB 1955', 'Celle')),
    P('I44', 'Johann', 'Oberlin', 'M', ('23 MAY 1865', 'Uelzen'), ('1 NOV 1940', 'Uelzen'), occu='Landwirt'),
    P('I45', 'Wilhelmine', 'Drewes', 'F', ('16 OCT 1869', 'Uelzen'), ('28 JAN 1947', 'Uelzen')),
    P('I46', 'Ernst', 'Ilgner', 'M', ('11 MAR 1862', 'Lueneburg'), ('20 SEP 1929', 'Lueneburg'), occu='Salinenarbeiter'),
    P('I47', 'Auguste', 'Pohlmann', 'F', ('2 DEC 1867', 'Lueneburg'), ('5 JUL 1943', 'Lueneburg')),
    P('I62', 'Louise', 'Falkenrath', 'F', ('2 MAY 1864', 'Celle'), ('19 NOV 1944', 'Celle'), portrait='f-1887'),
    P('I63', 'Hinrich', 'Bartels', 'M', ('28 DEC 1859', 'Celle'), ('10 FEB 1927', 'Celle'), occu='Sattler'),
    P('I65', 'Ernst', 'Falkenrath', 'M', ('7 AUG 1869', 'Celle'), ('14 MAY 1941', 'Milwaukee'), occu='Zimmermann, später Bauunternehmer', portrait='m-1890',
      facts=[('EMIG', 'Mit der „Fulda“ des Norddeutschen Lloyd', '3 MAY 1888', 'Bremerhaven'), ('IMMI', '', '17 MAY 1888', 'Milwaukee'), ('NATU', '', '1894', 'Milwaukee')],
      note='Ging als Neunzehnjähriger nach Amerika und holte 1891 seine Verlobte nach.\nSein Bauunternehmen in Milwaukee bestand bis 1938.'),
    P('I66', 'Clara', 'Steinbach', 'F', ('23 JUN 1870', 'Celle'), ('9 OCT 1952', 'Milwaukee'), portrait='f-1891'),
    P('I61', 'Wilhelm', 'Meyerhof', 'M', ('30 JAN 1860', 'Eschede'), ('12 DEC 1931', 'Eschede'), occu='Hofbesitzer'),
    # Generation 5
    P('I48', 'Friedrich Wilhelm', 'Falkenrath', 'M', ('6 JUL 1834', 'Eschede'), ('22 FEB 1901', 'Celle'), occu='Zimmermann', portrait='m-1875',
      facts=[('RESI', 'Zog als Geselle von Eschede in die Stadt', '1858', 'Celle')], note='Mit ihm beginnen die Einträge in der Familienbibel.'),
    P('I49', 'Johanne', 'Meinecke', 'F', ('17 JAN 1839', 'Celle'), ('30 AUG 1912', 'Celle'), portrait='f-1875'),
    P('I50', 'Christian', 'Wichmann', 'M', ('9 SEP 1838', 'Celle'), ('14 NOV 1899', 'Celle'), occu='Schuhmacher'),
    P('I51', 'Luise', 'Brandes', 'F', ('21 FEB 1843', 'Celle'), ('7 APR 1920', 'Celle')),
    P('I58', 'Sophie Dorothee', 'Falkenrath', 'F', ('9 NOV 1831', 'Eschede'), ('25 APR 1902', 'Eschede')),
    P('I59', 'Ludwig', 'Meyerhof', 'M', ('3 FEB 1828', 'Eschede'), ('17 OCT 1890', 'Eschede'), occu='Hofbesitzer'),
    P('I60', 'Heinrich', 'Falkenrath', 'M', ('21 JUN 1837', 'Eschede'), ('4 AUG 1864', 'Celle'), occu='Zimmergeselle', note='Ertrank beim Flößen auf der Aller. Unverheiratet.'),
    P('I101', 'Johann', 'Ilgner', 'M', ('15 MAR 1830', 'Lueneburg'), ('8 JUL 1901', 'Lueneburg'), occu='Salinenarbeiter', portrait='m-1870'),
    P('I102', 'Dorothee', 'Reese', 'F', ('27 SEP 1836', 'Lueneburg'), ('21 FEB 1914', 'Lueneburg')),
    # Generation 6 und 7
    P('I52', 'Johann Heinrich', 'Falkenrath', 'M', ('12 OCT 1801', 'Eschede'), ('3 JAN 1872', 'Eschede'), occu='Häusling und Tagelöhner', facts=[('CHR', '', '18 OCT 1801', 'Eschede')]),
    P('I53', 'Catharine Sophie', 'Eggers', 'F', ('28 MAR 1806', 'Eschede'), ('19 JUN 1880', 'Eschede')),
    P('I55', 'Anna Margarethe', 'Falkenrath', 'F', ('6 JAN 1804', 'Eschede'), ('12 MAR 1871', 'Eschede')),
    P('I56', 'Jürgen Hinrich', 'Bostelmann', 'M', ('ABT 1798', 'Eschede'), ('30 MAY 1866', 'Eschede'), occu='Kötner'),
    P('I57', 'Heinrich', 'Bostelmann', 'M', ('14 AUG 1832', 'Eschede'), ('2 FEB 1899', 'Eschede'), occu='Kötner'),
    P('I54', 'Hans Jürgen', 'Falkenrath', 'M', ('ABT 1770', 'Eschede'), ('11 DEC 1831', 'Eschede'), occu='Häusling',
      note='Ältester bekannter Träger des Namens. Seine Frau wird in den Taufeinträgen der Kinder nicht genannt.'),
]

# Familien: (xref, Mann, Frau, Heirat (Datum, Ort) | None, Kinder, Scheidung | None, Notiz)
FAMILIES = [
    ('F1', 'I1', 'I2', ('18 JUN 2015', 'Hannover'), ['I3', 'I4'], None, None),
    ('F2', 'I6', 'I5', ('3 OCT 2017', 'Bremen'), ['I7'], None, None),
    ('F3', 'I8', 'I9', ('12 MAY 1983', 'Celle'), ['I1', 'I5'], None, None),
    ('F4', 'I11', 'I10', ('20 AUG 1982', 'Celle'), ['I12', 'I13'], '1999', None),
    ('F5', 'I15', 'I17', ('7 OCT 1950', 'Celle'), ['I18'], None, 'Erste Ehe von Wilhelm Falkenrath.'),
    ('F6', 'I15', 'I16', ('24 APR 1954', 'Uelzen'), ['I8', 'I10'], None, None),
    ('F7', 'I19', 'I20', ('15 JUN 1956', 'Lueneburg'), ['I9', 'I14', 'I86'], None, None),
    ('F8', 'I21', 'I22', ('17 MAY 1924', 'Celle'), ['I23', 'I15', 'I25'], None, None),
    ('F9', 'I24', 'I23', ('5 DEC 1942', 'Celle'), [], None, 'Kriegstrauung während eines Fronturlaubs.'),
    ('F10', 'I25', 'I26', ('11 JUN 1956', 'Milwaukee'), ['I27'], None, None),
    ('F11', 'I28', 'I29', ('9 SEP 1927', 'Uelzen'), ['I35', 'I16'], None, None),
    ('F12', 'I30', 'I31', ('21 APR 1922', 'Lueneburg'), ['I19', 'I82'], None, None),
    ('F13', 'I32', 'I33', ('12 OCT 1928', 'Lueneburg'), ['I20', 'I34', 'I95'], None, None),
    ('F14', 'I36', 'I35', ('2 JUN 1953', 'Uelzen'), ['I37'], None, None),
    ('F15', 'I39', 'I40', ('14 SEP 1893', 'Celle'), ['I41', 'I21', 'I70', 'I71'], None, None),
    ('F16', 'I42', 'I43', ('8 MAY 1896', 'Celle'), ['I79', 'I22', 'I38'], None, None),
    ('F17', 'I44', 'I45', ('19 NOV 1891', 'Uelzen'), ['I98', 'I28'], None, None),
    ('F18', 'I46', 'I47', ('3 FEB 1890', 'Lueneburg'), ['I30'], None, None),
    ('F19', 'I48', 'I49', ('26 OCT 1862', 'Celle'), ['I62', 'I39', 'I65'], None, None),
    ('F20', 'I50', 'I51', ('30 APR 1866', 'Celle'), ['I40'], None, None),
    ('F21', 'I52', 'I53', ('15 NOV 1829', 'Eschede'), ['I58', 'I48', 'I60'], None, None),
    ('F22', 'I54', None, None, ['I52', 'I55'], None, None),
    # Seitenlinien und der fruehe Auswandererzweig
    ('F23', 'I56', 'I55', ('11 NOV 1827', 'Eschede'), ['I57'], None, None),
    ('F24', 'I59', 'I58', ('20 MAY 1855', 'Eschede'), ['I61'], None, None),
    ('F25', 'I63', 'I62', ('16 OCT 1887', 'Celle'), ['I64'], None, None),
    ('F26', 'I65', 'I66', ('22 AUG 1891', 'Milwaukee'), ['I67', 'I68'], None, 'Clara reiste 1891 allein über Bremerhaven nach; getraut wurde in der deutschen Gemeinde St. Johannes.'),
    ('F27', 'I67', 'I103', ('14 JUN 1919', 'Milwaukee'), ['I69'], None, None),
    ('F28', 'I72', 'I71', ('3 JUL 1926', 'Celle'), ['I73', 'I74'], None, None),
    ('F29', 'I74', 'I85', ('19 SEP 1956', 'Celle'), ['I75'], None, None),
    ('F30', 'I76', 'I41', ('27 MAR 1919', 'Hannover'), ['I77', 'I78'], None, None),
    ('F31', 'I79', 'I80', ('8 OCT 1925', 'Celle'), ['I81'], None, None),
    ('F32', 'I83', 'I82', ('30 APR 1955', 'Lueneburg'), ['I84'], None, None),
    ('F33', 'I87', 'I86', ('15 JUN 1988', 'Lueneburg'), ['I88', 'I89'], None, None),
    ('F34', 'I96', 'I95', ('7 MAY 1960', 'Lueneburg'), ['I97'], None, None),
    ('F35', 'I98', 'I99', ('12 NOV 1921', 'Uelzen'), ['I100'], None, None),
    ('F36', 'I27', 'I92', ('21 JUN 1986', 'Milwaukee'), ['I93', 'I94'], None, None),
    ('F37', 'I12', 'I90', ('9 AUG 2013', 'Celle'), ['I91'], None, None),
    ('F38', 'I101', 'I102', ('2 JUN 1859', 'Lueneburg'), ['I46'], None, None),
]

# Quellenangaben: (Datensatz, Ereignis-Tag) -> (Quelle, Seite)
CITATIONS = {
    ('I52', 'BIRT'): ('S1', 'Taufen 1801, Nr. 23'),
    ('I52', 'CHR'): ('S1', 'Taufen 1801, Nr. 23'),
    ('I54', 'DEAT'): ('S1', 'Begräbnisse 1831, Nr. 41'),
    ('F21', 'MARR'): ('S1', 'Trauungen 1829, Nr. 9'),
    ('F19', 'MARR'): ('S3', 'Vorsatzblatt, erster Eintrag'),
    ('I39', 'BIRT'): ('S3', 'Vorsatzblatt'),
    ('F15', 'MARR'): ('S2', '1893, Nr. 112'),
    ('F8', 'MARR'): ('S2', '1924, Nr. 58'),
    ('I55', 'BIRT'): ('S1', 'Taufen 1804, Nr. 3'),
    ('F23', 'MARR'): ('S1', 'Trauungen 1827, Nr. 12'),
    ('I65', 'EMIG'): ('S3', 'Vorsatzblatt: „Ernst nach Amerika, Mai 1888“'),
    ('I70', 'DEAT'): ('S4', 'Ausgabe 2247, Seite 28511'),
}

# Dokumente und weitere Bilder: (Datei, Titel, Art, verknuepft mit)
EXTRA_MEDIA = [
    ('taufeintrag-1801.jpg', 'Taufeintrag Johann Heinrich Falkenrath, 1801', 'document', ['I52']),
    ('heiratsurkunde-1924.jpg', 'Heiratsurkunde Falkenrath – Rosskamp, 1924', 'document', ['F8']),
    ('schiffsliste-1952.jpg', 'Auszug aus der Passagierliste, 1952', 'document', ['I25']),
    ('familienbibel.jpg', 'Vorsatzblatt der Familienbibel', 'document', ['I48', 'S3']),
    ('tischlerei.jpg', 'Die Tischlerei Am Heiligen Kreuz, um 1960', 'photo', ['I15', 'I21']),
    ('hochzeit-1924.jpg', 'Hochzeit von Heinrich und Anna, 1924', 'photo', ['F8']),
]


# ── GEDCOM ───────────────────────────────────────────────────────────

def place_lines(level, key):
    name, lat, lng = PLACES[key]
    return [
        f'{level} PLAC {name}', f'{level + 1} MAP',
        f"{level + 2} LATI {'N' if lat >= 0 else 'S'}{abs(lat):.4f}", f"{level + 2} LONG {'E' if lng >= 0 else 'W'}{abs(lng):.4f}",
    ]


def event(tag, date, place, owner, value='', etype=None):
    lines = [f'1 {tag}' + (f' {value}' if value else '')]
    if etype:
        lines.append(f'2 TYPE {etype}')
    if date:
        lines.append(f'2 DATE {date}')
    if place:
        lines += place_lines(2, place)
    if (owner, tag) in CITATIONS:
        source, page = CITATIONS[(owner, tag)]
        lines += [f'2 SOUR @{source}@', f'3 PAGE {page}']
    return lines


def note_lines(level, text):
    first, *rest = text.split('\n')
    return [f'{level} NOTE {first}'] + [f'{level + 1} CONT {line}' for line in rest]


def build_gedcom():
    fams_of, famc_of = {}, {}
    for fam, husb, wife, *_rest in FAMILIES:
        children = _rest[1]
        for spouse in (husb, wife):
            if spouse:
                fams_of.setdefault(spouse, []).append(fam)
        for child in children:
            famc_of[child] = fam

    media = []  # (xref, datei, titel, art)
    links = {}  # datensatz -> [media-xref]

    def add_media(filename, title, kind, owners):
        xref = f'M{len(media) + 1}'
        media.append((xref, filename, title, kind))
        for owner in owners:
            links.setdefault(owner, []).append(xref)

    for person in PEOPLE:
        if person['portrait']:
            name = f"{person['given']} {person['surname']}"
            add_media(f"portrait-{person['xref'].lower()}.jpg", f'{name}, Portrait', 'photo', [person['xref']])
    for filename, title, kind, owners in EXTRA_MEDIA:
        add_media(filename, title, kind, owners)

    out = [
        '0 HEAD', '1 SOUR webtreesAnd-demo', '2 NAME Demo-Stammbaum Familie Falkenrath', '2 VERS 1.0',
        '1 GEDC', '2 VERS 5.5.1', '2 FORM LINEAGE-LINKED', '1 CHAR UTF-8', '1 LANG German',
        '1 NOTE Alle Personen und Lebensdaten dieses Stammbaums sind frei erfunden. Daten und Bilder: CC0.',
    ]

    for p in PEOPLE:
        x = p['xref']
        out += [f'0 @{x}@ INDI', f"1 NAME {p['given']} /{p['surname']}/", f"2 GIVN {p['given']}", f"2 SURN {p['surname']}"]
        if p['call']:
            out.append(f"2 NICK {p['call']}")
        out.append(f"1 SEX {p['sex']}")
        if p['birth']:
            out += event('BIRT', p['birth'][0], p['birth'][1], x)
        for tag, value, date, place, *etype in p['facts']:
            out += event(tag, date, place, x, value, etype[0] if etype else None)
        if p['occu']:
            out.append(f"1 OCCU {p['occu']}")
        if p['death']:
            out += event('DEAT', p['death'][0], p['death'][1], x)
        if p['note']:
            out += note_lines(1, p['note'])
        for m in links.get(x, []):
            out.append(f'1 OBJE @{m}@')
        if x in famc_of:
            out.append(f'1 FAMC @{famc_of[x]}@')
        for fam in fams_of.get(x, []):
            out.append(f'1 FAMS @{fam}@')

    for fam, husb, wife, marriage, children, divorce, note in FAMILIES:
        out.append(f'0 @{fam}@ FAM')
        if husb:
            out.append(f'1 HUSB @{husb}@')
        if wife:
            out.append(f'1 WIFE @{wife}@')
        if marriage:
            out += event('MARR', marriage[0], marriage[1], fam)
        if divorce:
            out += ['1 DIV', f'2 DATE {divorce}']
        if note:
            out += note_lines(1, note)
        for m in links.get(fam, []):
            out.append(f'1 OBJE @{m}@')
        for child in children:
            out.append(f'1 CHIL @{child}@')

    for xref, (title, author, text) in SOURCES.items():
        out += [f'0 @{xref}@ SOUR', f'1 TITL {title}', f'1 AUTH {author}']
        if text:
            out.append(f'1 TEXT {text}')
        for m in links.get(xref, []):
            out.append(f'1 OBJE @{m}@')

    for xref, filename, title, kind in media:
        out += [f'0 @{xref}@ OBJE', f'1 FILE {filename}', '2 FORM jpg', f'3 TYPE {kind}', f'2 TITL {title}']

    out.append('0 TRLR')
    (OUT / 'falkenrath.ged').write_text('\n'.join(out) + '\n', encoding='utf-8')
    return media


# ── Bilder ───────────────────────────────────────────────────────────

FONT_DIR = Path('/usr/share/fonts/opentype/urw-base35')


def font(name, size):
    for candidate in (FONT_DIR / name, FONT_DIR / 'P052-Italic.otf'):
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def old_photo(image, tone, rng):
    """Eine Zeichnung wie einen alten Abzug aussehen lassen: Toenung, Koernung, Vignette, Kartonrand."""
    w, h = image.size
    gray = ImageOps.grayscale(image).filter(ImageFilter.GaussianBlur(1.2))
    toned = ImageOps.colorize(gray, black=tone[0], white=tone[1])

    noise = Image.effect_noise((w, h), 18).convert('L')
    toned = Image.blend(toned, Image.merge('RGB', (noise, noise, noise)), 0.07)

    vignette = Image.new('L', (w, h), 0)
    ImageDraw.Draw(vignette).ellipse((-w * 0.25, -h * 0.2, w * 1.25, h * 1.2), fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(w * 0.18))
    toned = Image.composite(toned, Image.new('RGB', (w, h), tone[0]), vignette)

    card = Image.new('RGB', (w + 60, h + 90), tone[2])
    card.paste(toned, (30, 30))
    ImageDraw.Draw(card).rectangle((29, 29, w + 30, h + 30), outline=tone[0])
    return card


def portrait(path, style, seed):
    """Stilisiertes Brustbild - bewusst eine Zeichnung, keine Nachahmung eines echten Fotos."""
    rng = random.Random(seed)
    sex, year = style.split('-')[0], int(style.split('-')[1][:4])
    w, h = 600, 750
    image = Image.new('L', (w, h), 150)
    d = ImageDraw.Draw(image)

    # Atelier-Hintergrund mit weichem Verlauf
    for y in range(h):
        d.line((0, y, w, y), fill=int(175 - 70 * y / h + rng.uniform(-2, 2)))

    skin = rng.randint(200, 225)
    dark = rng.randint(25, 55)
    cx = w // 2 + rng.randint(-10, 10)

    # Schultern und Kleidung
    d.ellipse((cx - 250, 520, cx + 250, 980), fill=dark)
    if sex == 'm':
        d.polygon([(cx - 60, 540), (cx, 680), (cx + 60, 540)], fill=235)            # Hemd
        d.polygon([(cx - 14, 560), (cx + 14, 560), (cx + 9, 660), (cx - 9, 660)], fill=dark + 20)  # Krawatte
        d.polygon([(cx - 60, 540), (cx - 130, 600), (cx - 10, 700)], fill=dark + 12)  # Revers
        d.polygon([(cx + 60, 540), (cx + 130, 600), (cx + 10, 700)], fill=dark + 12)
    else:
        d.ellipse((cx - 85, 520, cx + 85, 640), fill=skin - 8)                        # Ausschnitt
        d.arc((cx - 95, 505, cx + 95, 655), 20, 160, fill=240, width=5)               # Kragen/Kette
        if year < 1935:
            d.ellipse((cx - 16, 610, cx + 16, 642), fill=225)                         # Brosche

    # Hals und Kopf
    d.rectangle((cx - 42, 430, cx + 42, 560), fill=skin - 14)
    head = (cx - 118, 170, cx + 118, 480)
    d.ellipse(head, fill=skin)

    # Haare
    hair = rng.randint(30, 90) if year < 1990 else rng.randint(40, 120)
    if sex == 'm':
        d.pieslice((cx - 124, 150, cx + 124, 400), 180, 360, fill=hair)
        d.polygon([(cx - 124, 275), (cx - 60, 215), (cx + 30, 205), (cx + 124, 275), (cx + 124, 250), (cx - 124, 250)], fill=hair)
        d.ellipse((cx - 108, 230, cx + 108, 330), fill=skin)                          # Stirn freilegen
        if year < 1915:                                                               # Bart der Kaiserzeit
            d.ellipse((cx - 70, 395, cx + 70, 450), fill=hair)
            d.ellipse((cx - 56, 380, cx + 56, 415), fill=skin)
        elif year < 1945 and rng.random() < 0.6:
            # breiter Schnauzer - bewusst kein schmaler Bart unter der Nase
            d.ellipse((cx - 58, 392, cx + 58, 416), fill=hair)
            d.ellipse((cx - 40, 404, cx + 40, 426), fill=skin)
    else:
        d.ellipse((cx - 140, 140, cx + 140, 420), fill=hair)
        d.ellipse((cx - 104, 215, cx + 104, 480), fill=skin)
        if year < 1940:
            d.ellipse((cx - 70, 110, cx + 70, 210), fill=hair)                        # Dutt
        else:
            d.ellipse((cx - 150, 300, cx - 80, 470), fill=hair)                       # Wellen
            d.ellipse((cx + 80, 300, cx + 150, 470), fill=hair)

    # Gesicht: nur Andeutungen
    eye_y = 330 + rng.randint(-6, 6)
    for ex in (cx - 46, cx + 46):
        d.ellipse((ex - 9, eye_y - 6, ex + 9, eye_y + 6), fill=60)
        d.line((ex - 20, eye_y - 24, ex + 20, eye_y - 28 + rng.randint(-3, 3)), fill=hair, width=5)
    d.line((cx, eye_y + 10, cx - 8, eye_y + 62), fill=skin - 45, width=4)
    d.arc((cx - 36, eye_y + 78, cx + 36, eye_y + 112), 20, 160, fill=110, width=5)

    tones = {
        1860: ((58, 40, 24), (238, 222, 190), (226, 210, 180)),
        1915: ((48, 38, 30), (236, 228, 210), (232, 224, 205)),
        1950: ((30, 30, 32), (240, 240, 238), (245, 243, 238)),
    }
    tone = tones[max(k for k in tones if k <= max(year, 1860))]
    old_photo(image.convert('RGB'), tone, rng).save(path, quality=88)


def parchment(w, h, rng):
    base = Image.new('RGB', (w, h), (236, 223, 190))
    noise = Image.effect_noise((w, h), 26).convert('L').filter(ImageFilter.GaussianBlur(2))
    base = Image.blend(base, ImageOps.colorize(noise, (190, 165, 120), (250, 242, 220)), 0.35)
    d = ImageDraw.Draw(base)
    for _ in range(6):                                                                # Stockflecken
        x, y, r = rng.randint(0, w), rng.randint(0, h), rng.randint(10, 40)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(214, 190, 140))
    base = base.filter(ImageFilter.GaussianBlur(0.8))
    d = ImageDraw.Draw(base)
    d.line((w // 2, 0, w // 2, h), fill=(205, 185, 145), width=2)                     # Faltlinie
    return base, d


def document(path, title, lines, seed, stamp=None):
    rng = random.Random(seed)
    w, h = 900, 1200
    image, d = parchment(w, h, rng)
    ink = (52, 40, 28)
    d.rectangle((40, 40, w - 40, h - 40), outline=ink, width=2)
    d.text((w // 2, 110), title, font=font('P052-Bold.otf', 40), fill=ink, anchor='mm')
    d.line((120, 150, w - 120, 150), fill=ink, width=2)

    y = 210
    script = font('Z003-MediumItalic.otf', 38)
    for line in lines:
        if line == '':
            y += 28
            continue
        d.text((100 + rng.randint(-3, 3), y), line, font=script, fill=ink)
        d.line((95, y + 46, w - 95, y + 46), fill=(170, 150, 115), width=1)
        y += 58

    if stamp:
        cx, cy, r = w - 230, h - 230, 95
        layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(60, 50, 120, 170), width=5)
        ld.ellipse((cx - r + 14, cy - r + 14, cx + r - 14, cy + r - 14), outline=(60, 50, 120, 170), width=2)
        ld.text((cx, cy), stamp, font=font('P052-Bold.otf', 22), fill=(60, 50, 120, 190), anchor='mm', align='center')
        image = Image.alpha_composite(image.convert('RGBA'), layer.rotate(-12, center=(cx, cy))).convert('RGB')

    image.save(path, quality=88)


def scene(path, kind, seed):
    """Zwei gezeichnete 'Fotos': die Werkstatt und ein Hochzeitspaar."""
    rng = random.Random(seed)
    w, h = 900, 640
    image = Image.new('L', (w, h), 190)
    d = ImageDraw.Draw(image)
    for y in range(h):
        d.line((0, y, w, y), fill=int(205 - 60 * y / h))

    if kind == 'workshop':
        d.rectangle((0, 470, w, h), fill=95)                                          # Strasse
        d.rectangle((150, 210, 750, 480), fill=150)                                   # Haus
        d.polygon([(120, 215), (450, 60), (780, 215)], fill=80)                       # Dach
        for x in range(150, 750, 60):                                                 # Fachwerk
            d.line((x, 210, x, 480), fill=70, width=6)
        d.line((150, 340, 750, 340), fill=70, width=6)
        d.rectangle((390, 350, 510, 480), fill=45)                                    # Tor
        for x in (210, 570, 630, 270):
            d.rectangle((x, 250, x + 44, 320), fill=215)
        d.rectangle((330, 290, 570, 330), fill=225)                                   # Firmenschild
        d.text((450, 310), 'H. FALKENRATH · TISCHLEREI', font=font('P052-Bold.otf', 17), fill=40, anchor='mm')
        tone = ((30, 30, 32), (240, 240, 238), (245, 243, 238))
    else:
        d.rectangle((0, 520, w, h), fill=110)
        for cx, dark, veil in ((360, 35, False), (540, 235, True)):
            d.ellipse((cx - 110, 300, cx + 110, 760), fill=dark)                      # Koerper
            d.ellipse((cx - 50, 170, cx + 50, 300), fill=215)                         # Kopf
            if veil:
                d.polygon([(cx - 70, 190), (cx + 70, 190), (cx + 130, 520), (cx - 130, 520)], fill=245)
                d.ellipse((cx - 50, 175, cx + 50, 300), fill=215)
                d.ellipse((cx - 60, 320, cx + 10, 390), fill=200)                     # Strauss
            else:
                d.pieslice((cx - 54, 160, cx + 54, 260), 180, 360, fill=40)
                d.polygon([(cx - 22, 300), (cx, 360), (cx + 22, 300)], fill=235)
        tone = ((48, 38, 30), (236, 228, 210), (232, 224, 205))

    old_photo(image.convert('RGB'), tone, rng).save(path, quality=88)


def build_media(media):
    MEDIA.mkdir(parents=True, exist_ok=True)
    portraits = {f"portrait-{p['xref'].lower()}.jpg": p['portrait'] for p in PEOPLE if p['portrait']}

    for index, (_xref, filename, _title, _kind) in enumerate(media):
        path = MEDIA / filename
        if filename in portraits:
            portrait(path, portraits[filename], seed=index * 7 + 3)

    document(MEDIA / 'taufeintrag-1801.jpg', 'Kirchenbuch Eschede · Taufen 1801', [
        'No. 23', '', 'Den 12ten October ist dem Häusling', 'Hans Jürgen Falkenrath alhier ein Sohn',
        'gebohren und den 18ten ejusdem getauft', 'worden, genannt', '', '        Johann Heinrich.', '',
        'Gevattern: Heinrich Eggers, Ackermann,', 'Johann Lüders, Schäfer zu Eschede.',
    ], seed=11)
    document(MEDIA / 'heiratsurkunde-1924.jpg', 'Heiratsurkunde', [
        'Nr. 58', 'Celle, am 17. Mai 1924.', '', 'Vor dem unterzeichneten Standesbeamten', 'erschienen heute zum Zwecke der',
        'Eheschließung:', '', '1. der Tischler Heinrich Falkenrath,', '   geboren am 26. April 1897 zu Celle,', '',
        '2. die Anna Rosskamp, ohne Beruf,', '   geboren am 13. August 1901 zu Celle.', '', 'Der Standesbeamte',
    ], seed=12, stamp='STANDESAMT\nCELLE')
    document(MEDIA / 'schiffsliste-1952.jpg', 'List of Passengers · MS Gripsholm', [
        'Bremerhaven — New York, April 1952', '', 'No.   Name                        Age   Occupation',
        '211   Falkenrath, Otto          22    toolmaker', '212   Fehrmann, Ilse             31    housewife',
        '213   Feldhusen, Gerd           19    farm hand', '', 'Destination: Milwaukee, Wisconsin',
    ], seed=13, stamp='ADMITTED\nAPR 25 1952')
    document(MEDIA / 'familienbibel.jpg', 'Familien-Chronik', [
        'Im Jahre des Herrn 1862 den 26ten October', 'bin ich, Friedrich Wilhelm Falkenrath,', 'mit meiner lieben Johanne geb. Meinecke',
        'in den Stand der Ehe getreten.', '', 'Unsere Kinder:', '1864 den 2ten May  Louise', '1866 den 19ten November  Carl', '1869 den 7ten August  Ernst',
        '', 'Gott segne unser Haus.',
    ], seed=14)
    scene(MEDIA / 'tischlerei.jpg', 'workshop', seed=21)
    scene(MEDIA / 'hochzeit-1924.jpg', 'wedding', seed=22)


README = """# Demo-Stammbaum „Familie Falkenrath“

Ein kleiner, **frei erfundener** Stammbaum zum Ausprobieren und Testen von webtrees, dem Modul
*api4webtrees* und der App *wtAnd*: {people} Personen, {families} Familien, acht Generationen
(um 1770 bis heute), {media} Bilder.

**Alle Personen, Lebensdaten und Dokumente sind ausgedacht.** Übereinstimmungen mit lebenden oder
verstorbenen Personen sind Zufall. Die Orte gibt es wirklich – sie tragen Koordinaten, damit Karten etwas
zu zeigen haben. Die Bilder sind gezeichnet (`tools/make_demo_tree.py`), nichts stammt aus fremden Quellen.

Lizenz: [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.de) – frei verwendbar, auch für
Screenshots und Vorführungen.

## Was der Baum abdeckt

Lebende Personen (Datenschutz), zwei Ehen mit früh verstorbenem Kind, Gefallene beider Weltkriege,
zwei Auswanderungen nach Milwaukee (1888 und 1952) samt amerikanischem Zweig, eine Scheidung, eine unbekannte
Mutter an der Spitze, Vettern und Cousinen auf Vater- und Mutterseite, Quellen mit Seitenangaben, Notizen,
Rufname, Orte mit Koordinaten, Medien an Personen, Familien und Quellen.
Startperson: **Jonas Falkenrath (I1)**.

## In webtrees laden

1. *Verwaltung → Stammbäume verwalten → Stammbaum anlegen*, z. B. `falkenrath`.
2. `falkenrath.ged` importieren.
3. Dem Baum einen **eigenen Medienordner** geben (*Einstellungen → Medienordner*, z. B. `media/falkenrath/`)
   und die Dateien aus `media/` dorthin hochladen (*Verwaltung → Medien → Mediendateien hochladen*).
"""


def main():
    OUT.mkdir(exist_ok=True)
    media = build_gedcom()
    build_media(media)
    (OUT / 'README.md').write_text(README.format(people=len(PEOPLE), families=len(FAMILIES), media=len(media)), encoding='utf-8')
    print(f'{len(PEOPLE)} Personen, {len(FAMILIES)} Familien, {len(SOURCES)} Quellen, {len(media)} Medien -> {OUT}')


if __name__ == '__main__':
    main()
