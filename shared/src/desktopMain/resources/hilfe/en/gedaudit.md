# gedaudit

**gedaudit** checks a GEDCOM file for contradictions and oddities, using the same rules as the plausibility check in wtWin, wtTux and wtMac. It needs neither webtrees nor a server: the file is only read, nothing is changed, nothing leaves your computer.

## Opening a file

**File › Open GEDCOM file …** (Ctrl+O) or the button on the start page. Recently opened files are listed on the start page and under **File › Recently opened**. A `.ged` file can also be opened with gedaudit by double-clicking it in the file manager.

GEDCOM 5.5, 5.5.1 and 7 are read. The character set is taken from the file (UTF-8, UTF-16, ANSEL, ANSI); the one detected is shown in the line above the findings. If the file was changed in the other program, **Reload file** (F5) picks up the new state.

## Checking

The rules are listed in groups on the left, the findings on the right. How rules, presets, limits and ticking off work is explained in the chapter [Plausibility check](hilfe:pruefung). The difference to wtWin: there is no person sheet and no editing, because gedaudit never writes to the file. Corrections are made in the genealogy program the file came from; then export the file again and reload it in gedaudit.

## Form of the file

The **Form** group exists for files only: lines that are not GEDCOM, links to persons or families that do not exist, identifiers used twice, dates in an unreadable form, persons without a name, more than one birth or death, family links recorded on one side only, a missing header or a character set in the header that does not match the file, and tags outside the 5.5.1 or 7 standard. The last two rules are off by default because some programs trigger them a lot.

**View › File structure …** (F6) shows the tag tree per record type with the number of occurrences. Vendor tags (with underscore) and tags outside the standard are highlighted. This shows what one program writes on export and what another will lose on import.

## Viewing a person

Clicking a finding shows the **person** below: events with date, place and number of sources, the parents, the partners with marriage and the children. If a finding concerns two persons, such as a possible duplicate or child and mother, both are shown side by side. The identifiers (I123, F45) are those from the file, so you can find the person again in the program of origin. The divider above the card can be dragged. **Ctrl+C** copies the person text of the selected person (name, identifier, events, parents, partners, children) as long as no input field has the focus; a right-click on a finding or an event offers **Copy person text** and **Copy row**, and the values of the card can be selected with the mouse. The right-click also offers **Search in the CompGen meta search …**: the surname and, if you like, a place go to meta.genealogy.net, and the hits from GEDBAS, online local family books and further databases open in the browser.

## Comparing two files

**File › Compare with another file …** (Ctrl+D) sets a second file against the open one, such as an older state, the export from another program or a fellow researcher’s file. Persons are matched by identifier, otherwise by name, birth year and sex; near matches are listed as “probably the same person”. On the left the groups: changed, probably the same, only in one file or the other, unchanged. A click on an entry shows the changed fields and both persons side by side. Relatives are compared by name, because identifiers may differ between files. Print and PDF output the list of differences. Nothing is merged and nothing is written.

## Ticking off

Findings you have checked and that stay as they are can be ticked off; they disappear from the list and only return when something changes at that spot. The tick list is stored on this PC, separately per file (by its path). **Save ticked …** and **Load ticked …** exchange it as a file, also with wtWin.

## Printing

**File › Print** (Ctrl+P) and **Save as PDF …** output the current list of findings with a summary.

## Language and appearance

**View › Language** switches the interface (German, English, French, Dutch, Spanish), **View › Appearance** between light and dark.

## GEDCOM-L

gedaudit knows the GEDCOM-L addendum to GEDCOM 5.5.1. In the **file structure** window its tags (_LOC, _GODP, _UID …) are marked “GEDCOM-L”, not as vendor tags; the header line names the addendum version if the file states it. The person card shows godparents and witnesses without a record of their own (_GODP, _WITN), the address and the GOV identifier, postal code and Maidenhead locator of the place. Pointers to location records (`3 _LOC` at a place, `1 _LOC` in a location record) whose target is missing are reported like any other broken pointer.
