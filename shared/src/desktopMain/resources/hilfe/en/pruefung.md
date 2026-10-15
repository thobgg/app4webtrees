# Plausibility check

**Tools › Plausibility check …** (Ctrl+Shift+P) or the **Check** icon checks the whole tree, as far as you may see it, with 77 rules. **Errors** are contradictions in the data (death before birth, own ancestor), **warnings** are unusual but possible (very young mother, godparent already dead, possible duplicate, place variant). With api4webtrees 1.11 or newer the godparent rules (024, 126) use the linked godparents and witnesses directly and look up free-text ones by name; with an older module they read the note “Paten: …”.

![](desktop-pruefung.jpg)


## Rules and limits

The rules are grouped on the left: **Chronology**, **Age limits**, **Structure**, **Names**, **Sources**, **Places**, **Form**. The Form group checks the structure of a GEDCOM file (links, date format, required entries); in wtWin only those of its rules apply that are visible in the tree. To check a file with all rules, including GEDCOM 7, use **Tools › Check GEDCOM file …**. A click on a rule shows only its findings, **All findings** shows everything again.

- **Preset:** from strict to generous; "Sources" additionally enables rules 420 and 421 (events and individuals without a source). As soon as you change something it becomes "Own settings".
- Per rule: **limit** (years or months, the default is shown), **severity** (error or warning), or switch the rule off.
- **Estimate:** derive missing birth and death from baptism and burial so that the age rules apply.
- **All rules and limits as default** resets, **Check again** recalculates with the current settings.

## Working with findings

Each finding names the person, the rule and the details concerned.

- A click on the finding opens the person; **Edit event** changes the date or place directly from the finding (with edit rights).
- **Tick as checked:** findings you have checked and that stay as they are (e.g. a documented late birth) disappear from the list. **Show ticked** brings them back, **Reopen** removes the tick.
- The tick list is stored on this PC, separately per server and tree. **Save ticked …** and **Load ticked …** exchange it as a file, e.g. with a fellow researcher or for a second computer.

## Printing

**Print** and **Save as PDF …** output the current list of findings with a summary. The line above says how many persons and families were checked and how many findings are errors.
