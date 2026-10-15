# Person sheet and editing

The person sheet opens with a **double-click** on a person, with **Ctrl+E** or via the **Edit** icon. It shows everything about a person and is also the place for editing.

## Layout

![](desktop-personenblatt.png)


Name, dates and picture on top. Below, the tabs:

- **Data:** all events as a table (event, date, place / description), plus age at death.
- **Life:** the timeline with marriage, births of the children and the age at each event. It also lists the events where the person was a godparent or witness (“Godmother at: Christening of …”); a click leads to the child or the couple.
- **Godparents and witnesses:** below baptism and marriage a line “Godparents:” or “Witnesses:” appears – in the timeline, in the detail area of the data table, in the family view and on index cards. Godparents with their own record are underlined and clickable, those without (from a note “Paten: …” or the GEDCOM-L field _GODP) appear as text. ⓘ unfolds a note about the godparent, the source icon opens the source. Living godparents you are not allowed to see show only as “Private”. Below the data table the section **Godparent and witness roles** lists all baptisms and marriages where the person was a godparent or witness; a click on the heading folds it away. Both need api4webtrees 1.11 or newer; with an older module the godparent note simply stays a note.
- **Type of marriage:** civil and religious marriage appear as separate events with their type; lists and the check use the civil one when both exist.
- **Parents/Siblings**: parents and siblings, including half-siblings. A click switches to that person.
- **Partners/Children**: the partnerships on the left, the children of the selected one on the right, below its events (marriage, divorce, residence …) to add, edit and delete. Double-click a partner or child to show their sheet; **+** adds a partner or a child of this partnership.
- **Name**: given names, surname and name suffix have their own fields when editing.
- **Simple / Full** (bottom of the sheet): “Simple” shows a form in the Data tab with name, birth, baptism, religion, occupation, marriage per partnership, death and burial to type into directly; “Full” shows the table of all events with age and markers for note and source (click a column header to sort).
  Only what you changed is saved; sources, notes and other details of the event are kept. If an event exists more than once, the form edits the first. A date the program cannot interpret (“spring 1850”) is saved as date text. Saving happens when you leave a field (Tab or click elsewhere), as everywhere in the program. An invalid date stays red and is not sent until you correct it or choose “Save as text anyway”; only then does closing or quitting ask first.
- **Notes**, **Sources**, **Media**.
- **Map:** the places of life as a list with links to OpenStreetMap.

## Paging

**Page Up** and **Page Down** move to the previous or next person in the list, **Ctrl+Home** and **Ctrl+End** to the first and last. **Esc** closes the window.

## Editing

![](desktop-paten-dialog.jpg)


Editing needs edit rights in webtrees. Changes go to webtrees immediately; depending on the tree's settings they apply at once or wait for a moderator's approval.

- **Change an event:** double-click the row (or Edit). Date and place are selected, not typed: exact, about, before, after, between; day, month, year. Places are suggested from the tree as you type.
- **Add event**, including family events such as marriage.
- **Add relative** (Ctrl+N): create parents, partner, child or sibling with name and first events. In the navigator this is also in the right-click menu.
- **Entering godparents and witnesses:** select the baptism or marriage in the data table and click **Edit godparents …** or **Edit witnesses …** in the detail area below (marriages also in the Partners/Children tab). Arrange the list with the arrows, ✕ removes an entry. **Person from the tree …** searches by name and links the person; **Without a record …** adds someone who has no entry of their own – as written in the church register: “Friedrich Plate, farmer at Celle”. The role (godparent, witness or another such as “midwife”) and a note about the person are below the list. Linked godparents are stored as webtrees does, people without a record as the GEDCOM-L fields _GODP and _WITN; sources on a godparent are kept. Godparents recorded only at the person (older exports) are moved into the baptism with a tick. Needs api4webtrees 1.12 or newer.
- **Type of marriage:** in the event dialog of a marriage, **Type** chooses between not stated, civil, religious, registered partnership and common-law. A second ceremony (such as the religious one after the civil one) is added as another “Marriage” event of the partnership.
- **Delete:** an event or the whole person, after confirmation.
- **Notes and media:** In the **Notes** tab you add general notes about the person (**+ New note**), change and delete them; notes on events are listed below for reading. In the **Media** tab you attach photos and scans (a file, an existing media object, one from the archive or simply dragged from the file manager), change title and type and remove links; a click on a picture opens it. Needs api4webtrees with API level 23.
- **Edit in webtrees:** everything the program cannot do itself (such as shared note records) you do on the person page in the browser. See [webtrees in the browser](hilfe:webtrees).

## Merging individuals

![](desktop-zusammenfuehren.jpg)


Duplicate individuals arise when two files are combined or while entering data – the same ancestor twice, once as a child and once as a husband. **Tools › Merge individuals …** (tree managers only, needs api4webtrees 1.16 or newer) lists all pairs the plausibility check takes for duplicates: same name and one identical event date (rule 219) or a similar name with a close year of birth (rule 228). In the check the same pairs show a symbol with two merging lines. **Add pair …** takes any two individuals, such as children with the same name and no data; in the table of individuals the right mouse button does the same.

**Merge …** shows who stays and who is absorbed, and below that only what is **added** from the second individual, usually one to three lines with tick boxes. Identical entries are not taken over twice, not even when one version has fewer details (birth with date only next to birth with date and place) or a “deceased” without a date stands next to a death with a date. **Show all events** opens the full side-by-side comparison in case you want to drop something from the first individual too. Links to families and media always stay, sources and notes at the events too. Everything that pointed to the second individual – families, source citations, godparent roles – points to the first afterwards. Below, the program suggests further pairs (father, mother, spouses and children with the same name); ticked ones follow after the merge. If an individual ends up with two births because the data contradict each other, the check shows it and you clean it up in the individual sheet.

**Undo:** every merge is listed in the **Log** tab with time and user. **Undo** restores both individuals and all links as they were – even days later, as long as nobody has edited the affected records since; otherwise the program names the changed records and leaves everything as it is. Without automatic acceptance, merging and undoing wait for a moderator like any change.

## Photos

![](desktop-gesichter.jpg)


**Add photo** picks a file from the PC and attaches it to the person; the picture is scaled to the server's upload limit. A click on a picture opens the viewer.

## Printing and sharing

- **File › Print person sheet …** (Ctrl+P) and **Person sheet as PDF …**
- **Person › Copy person text** (Ctrl+Shift+C) puts all details as text on the clipboard, e.g. for an e-mail or a word processor.
- **Person › Search in the CompGen meta search …** (also by right-clicking a person) searches the surname in the databases of the German Association for Computer Genealogy: GEDBAS, address books, online local family books, casualty lists and more. A small window shows what is sent – only the surname without particles such as “von”, a place of the person if you like, never given names or dates – and the results open in the browser. Without a place the search usually finds more.
- More output: [Lists](hilfe:listen) (person sheet as a list), [Charts](hilfe:tafeln), [Books](hilfe:buecher).

## GEDCOM-L

wtWin shows every item of the GEDCOM-L addendum a family tree contains (from api4webtrees 1.21): in the detail area of an event the address, the GOV identifier, the postal code (with date) and the Maidenhead locator of the place as well as the addressee; in the header of the person sheet, when hovering over the ID, the unique identifier (_UID); in the **Media** tab a star on the highlighted image (_PRIM); in the task window description, category, priority, type, status, reminder, completion date, solution and repository of a research task; in the place manager under **Data** earlier names and types with dates, postal codes with dates, administrative ID (e.g. municipality key), population figures, Maidenhead locator, religion and unique identifier. Editing keeps every GEDCOM-L item. You can edit the **family status** of a partnership (Partners/Children tab, choice above the events), the **main image** (Media tab, “Make main image” – moves it to the first place and marks it with _PRIM) and all fields of a **research task** in the task dialog (from api4webtrees 1.21).
