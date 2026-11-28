# Lists

Open the **Create list** window via **Create › Ancestor list**, **Descendant list**, **Event list** or the **List** icon. The list type with its settings on the left, the preview of the finished pages on the right; **Print** and **Save as PDF …** at the bottom.

## From the central person

![](liste-ahnen.jpg)
![](liste-stamm.jpg)
![](liste-taufpaten.jpg)
![](liste-ahnenwertung.jpg)


- **Ancestor list:** all ancestors by generation with Kekulé numbers, up to 12 generations; anyone appearing more than once refers to their first number (pedigree collapse).
- **End-of-line ancestors:** the ancestors where research ends, by the four grandparent lines.
- **Paternal line** and **Maternal line:** the father's or mother's line generation by generation, with spouses.
- **Ancestor completeness:** how completely each generation is researched, as a table with pedigree collapse.
- **Descendant list:** all descendants with numbers, spouses and marriages, up to 10 generations. Numbering after Saragossa, d'Aboville, Henry or serial.
- **Descendants per generation:** how many descendants there are in each generation: men, women, living, name bearers.
- **Person sheet:** all details of the central person on one page.

## Across the whole tree

![](liste-namen.jpg)
![](liste-orte.jpg)
![](liste-ereignisse.jpg)
![](liste-spitzenahnen.jpg)


- **Event list:** births and deaths of all visible persons in date order; optionally as an **anniversary calendar** by month and day, with a place filter, e.g. for the family newsletter.
- **Surname list:** all surnames with count, period and the most frequent places.
- **Place list:** all places as written at the events – **places only** (events, individuals, period), **with surnames** or **with individuals** and their events there. **Place details** adds GOV identifier and coordinates from the place manager, **Notes** and **Sources** those of the location record.
- **Family list:** all marriages with wedding and number of children, alphabetical or chronological.
- **Sourced:** how well the tree is sourced – individuals and events with a source in percent, photos, per event type, and who has no source at all yet.
- **Occupations and religions:** who had which occupation or belonged to which religion.
- **Godparents:** all baptisms with their godparents in date order; with api4webtrees 1.11 or newer from the linked and the free-text godparents (“linked: 2 of 3” tells how many have their own record), otherwise from the note “Paten: …”.

Lists across the whole tree need the tree in one piece; the server must run api4webtrees 1.9 or later. With the family tree on this PC that is always the case.

The preview shows the first pages; if there are more, "… and n more pages in the PDF" appears below. Each page carries "Created with wtWin on …" at the bottom.

## DNA inheritance lines

**Create › DNA inheritance lines …** shows for the starting person from which ancestors they can have Y-DNA, mitochondrial DNA (mtDNA) or X-DNA, and which descendants can have it from them. Choose the line, the direction and the number of generations at the top.

- **Y-DNA:** from father to son. For a woman the line of her father is shown, with a note that she has no Y chromosome herself.
- **mtDNA:** from the mother to all children; only daughters pass it on.
- **X-DNA:** a man has his X from his mother and passes it to his daughters, a woman has one from each parent and passes X to all children. Each generation states how many of the possible X ancestors are in the tree.

Ancestors are listed by generation with their ancestor number, descendants as an indented list – these are the people who could take a test for the line. A click makes the person the starting person in the main window; **Start from …** recalculates the lines for them.

The lines are derived from the recorded relationships and are not a test result; for X-DNA "possible" does not mean that X-DNA is actually shared. Only biological parents count: at an adopted or foster child and at a person without a recorded sex the line ends with a note. The menu item only appears if the api4webtrees module on the server offers the lines.
