# Search, go to, bookmarks

## Search (Ctrl+F)

**Ctrl+F** (or the **Search** icon) opens the search window with four tabs. It works on the whole family tree, loaded the same way as for the person table and the check – after that the search needs no server.

- **Search:** one term, read as exact text, as all words (AND) or as any of the words (OR); plus **whole field**, **match case** and **phonetic** (Cologne phonetics: Meyer finds Maier and Mayer). Under **Fields** you choose where to look: names, occupation, dates, places, sources, notes and the names of parents, partners, siblings and children.
- **Search list:** save the match list as a file (`.wtliste`) and load it again later.
- **Replace:** search for / replace with in places, occupations, notes and event values – never in names. First **Preview**: every hit with old and new text, each one can be unticked; then **Replace** writes through the API, event by event. Without auto-accept the changes wait for approval in webtrees. Needs edit permission.
- **Identifiers:** search by identifier (I123), user reference (REFN) or unique identifier (_UID) – anywhere, at the start, at the end or complete.

The match list at the bottom is shared by all tabs: click makes the person the central person, double-click opens the person sheet. **Search only within the current matches** narrows the next search to the list. **Add to bookmarks** bookmarks all matches, **Show in person table** opens the table with just these people.

## Go to

**Ctrl+G** (or **Person › Go to**) opens the quick name search: type a name, pick the match, and the person becomes the central person. In the other layouts the person list on the left also searches directly.
## Start person

**Alt+Home** jumps to the start person: the person webtrees knows for your account (your own record or the tree's default person). It is also the reference for colour coding.

**Person › Set as start person …** makes the central person the start person – just for you (as “My account” in webtrees) or, as a manager, for everyone as the family tree's default individual. Worth doing after taking over a GEDCOM file on this PC, otherwise the program starts with the first person of the file.

## Bookmarks

Keep the persons you are working on: **Ctrl+D**, **Person › Bookmark** or right-click › Bookmark. **Ctrl+B** opens the bookmark list; a click opens the person, **Remove** drops it. Bookmarks are available when the server module api4webtrees is recent enough.

## History

**Alt+Left** and **Alt+Right** go back and forward in the history; the **History** icon lists the last persons.

## Places, sources, duplicates

Searching for places and sources and "Find duplicates" are done by webtrees itself; the **webtrees** menu opens the matching pages in the browser. To see how two persons are related, use the **Relationship path** chart (see [Charts](hilfe:tafeln)).
