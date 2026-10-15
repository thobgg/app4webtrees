# Books

**Create › Create book …** turns the tree into a book in the style of printed local family books: every person with events, sources, godparents and notes, references to parents and children, portraits in the margin, table of contents and indexes.

## Three books

![](buch-vor-titel.jpg)
![](buch-vor-gen.jpg)
![](buch-nach-gen.jpg)
![](buch-fam.jpg)


- **Ancestor book:** all ancestors of the central person by generation and Kekulé number, with dates, baptisms, burials, sources and notes. The four grandparent lines optionally coloured in the margin.
- **Descendant book:** all descendants generation by generation, with spouses, children and references; numbers after Saragossa, d'Aboville, Henry or serial; branch colours per child of the root couple.
- **Family book:** one entry per family, alphabetical or chronological. With **layout “per person”** it becomes the person report: one entry per person with parents, marriages and children as pointers, indexes on person numbers. Also, one entry per family, alphabetical or chronological. With a **place filter** it becomes a local family book. Needs the whole tree in one piece (api4webtrees 1.9 or later on the server). **Houses and farms** (api4webtrees 1.15 or later) adds a section on the buildings: every farm and house of the place from the place manager (location records with a type below the place) with its history and its residents and owners in order of time, each pointing to the family; the families point back to their house (H1, H2 …). What counts as a house is decided by the type in the location record (house, farm, mill, church …; without a type, a house number in the name counts); parts of town and villages become chapters, places with residents that are not buildings go into the appendix “Other places”. If the location record carries a GOV type number (GEDCOM-L `2 _GOVTYPE`, api4webtrees 1.18.1 or later), that number decides before the text: farm, building, mill, estate, church … are houses, village, part of town, municipality … are levels above. The options “Only houses and farms” and “Include places without a type” control this. Place names may be separated by commas or semicolons.

## Settings

- **Data:** generations (2 to 12), notes, sources, shorten place names, show duplicate ancestors in full (instead of "see no.").
- **Living people:** for publishing, living people appear in full, with their name only or not at all; living means webtrees treats them as living or they are younger than the age limit (80 to 120 years). Family book and person report only.
- **Preface from file …** reads a text file into the preface; **Save template …/Load template …** keeps book type and all settings as a `.wtbuch` file, for instance per place.
- **Appearance:** pictures, colour coding, preface (your own text on the first page), chart as fold-out page (A3, PDF only).
- **Indexes:** names, places, occupations, sources, each pointing to the entry numbers.

## Saving

![](buch-vor-reg.jpg)
![](buch-nach-titel.jpg)


**Save book** asks for the format:

- **PDF** with bookmarks and links (a click on "see no." jumps to the entry).
- **DOCX** for further editing in Word or LibreOffice. Update the table of contents there once: click it and press F9 (LibreOffice: Tools › Update › Indexes and Tables).
- **Website:** a folder to upload – start page with preface and tiles, all entries on one page with a letter bar, every entry also as a page of its own with browsing, houses, indexes as pages, stylesheet and pictures as files. Set living people to “name only” first.
- **HTML** for a website, **TeX** for typesetting with LaTeX, **Text**.

For large trees, loading persons and pictures takes a moment; the window shows the progress.

## Large trees with many pictures

webtrees renders every thumbnail on the server when it is first requested – with thousands of people the first run takes a while. wtWin loads only the main picture per person, six at a time, shows the progress (“Pictures 2,340/8,900 – from this PC …, cache …, server …”) and can be stopped with **Continue without the remaining pictures**. Whatever has been loaded once stays in the cache on this PC (`~/.cache/app4webtrees/medien/<server>/<tree>/`, on Windows in the application data folder); the next run and the charts afterwards need nothing from the server. The book dialog shows the cache size and **Clear picture cache** under Appearance.

If you keep a copy of the webtrees media folder on this PC (for example because you upload by FTP), enter it as **Media folder on this PC**: pictures are then read from there and scaled locally, without the server. Everyone else leaves the field empty. webtrees always remains the source; the cache and the folder are copies only.

**Visibility:** Choose who the book is for: everything (manager view), what members may see, or what visitors may see. The book then leaves out what webtrees hides from that reader – restricted events (“Show to members”, “Show to managers”), data covered by the tree’s privacy rules, godparents and families the reader may not see, and the life data of living people; their names stay only if the tree shows living names to that reader, otherwise “Private” appears. Members therefore see more than visitors, but nothing shown only to managers. This way a book can be passed on even when a manager creates it. The website always contains only what visitors may see. Needs api4webtrees 1.21 or later.
