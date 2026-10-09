# Family tree on this PC

Without a server, wtWin creates the family tree on this computer. In the background runs a complete webtrees with bundled PHP, unchanged from the official release. It is reachable only on this PC, starts with the program and stops with it. You see no server and no password; the program signs in by itself.

## Creating

On first start, on the right: enter a name and click **Create family tree** (empty) or **Import from GEDCOM file …**. To move over from another program, export a GEDCOM file (`.ged`) there and choose it here. Persons, families, events, sources and notes are imported. The GEDCOM file itself stays unchanged; a second import creates another tree next to it and never overwrites.

The file may be UTF-8 (with or without byte order mark), UTF-16, ANSEL or ANSI; the character set is detected and converted as in the webtrees import. Single unusable records (such as duplicate IDs) are skipped and listed in `import.log`, the rest is imported.

Photos do not come along from the GEDCOM. Add them in the program, or copy them into the media folder later (see below) and link them in webtrees.

## Several family trees

**File › Family trees on this PC …** lists all trees with their number of people. There you can open one, rename it (change the title, then the tick or Enter) or delete it (never the last one), add another empty tree or import another GEDCOM file. Empty leftovers of earlier failed imports – only the webtrees sample person “John Doe” – are removed automatically at start-up; a tree you created empty yourself is kept.

## Where the data is

| System | Folder |
| - | - |
| Windows | `%LOCALAPPDATA%\app4webtrees` (type it into the Explorer address bar) |
| Linux | `~/.local/share/app4webtrees` |

Inside are `webtrees/` with the program and `webtrees/data/` with the database (SQLite) and the media folder `media/`. The PHP server's log is `php.log`. What wtWin itself notices (dropped connections, retries, server restarts) is written to `wtwin.log` next to it.

## Archive

The family tree on this PC comes with the **Sammlungen** (collections) module: photos and documents live as folders under `data/media`, need not be attached to individuals, and appear under **Photos › Archive** as well as in webtrees in the browser. Parish register scans from the archive can be assigned as a source or citation in the source manager.

## Backup

The program does not back up automatically. Two ways:

- With wtWin closed, copy the folder `app4webtrees`, e.g. to a USB stick. That is the complete backup including photos.
- **Open webtrees in browser** and export the tree as GEDCOM in the control panel. That saves the data, not the pictures.

## Everything from webtrees

**File › Open webtrees in browser** shows your webtrees in the browser: control panel, modules, changing names, creating sources. The browser asks for its own sign-in. The user name is your login name on the PC; the password was generated randomly when the tree was created and stored in the file `zugang.properties` in the folder `app4webtrees` (see above). Open the file with a text editor and copy the password. Do not pass it on; it is the manager login to your tree.

## Moving to a NAS or web host

If the family should read along or you work on two computers, the tree moves to a server, e.g. a Synology with nas4webtrees. Afterwards you use wtWin as before, just connected.

1. In wtWin **Open webtrees in browser**, there **Control panel › Family tree › Export** as GEDCOM.
2. Photos: copy the folder `webtrees/data/media` (see above) to `data/media` of the webtrees on the server.
3. On the server create a new tree and import the GEDCOM.
4. In wtWin **File › Sign out**, **Other address**, enter the server address. Or click **Connect with wtWin** on the server's **App** page.

The tree on the PC is kept until you delete the folder.

## If something goes wrong

If the tree cannot be created, the program shows a message with the path to `php.log`. Please report it at github.com/thobgg/app4webtrees/issues and attach the file, and when importing a GEDCOM file also `import.log` from the same folder (character set, skipped records, reason for failure). **Help › About wtWin** shows whether PHP and webtrees were found.

**“Connection … interrupted”:** If the connection to the family tree on this PC drops although webtrees works in the browser, another program is usually filtering wtWin’s network traffic – ad blocking or web protection, also the real-time protection of a virus scanner. An exception for wtWin (`%LOCALAPPDATA%\Programs\wtWin\wtWin.exe`) in that program usually helps. What wtWin notices is written to `wtwin.log`.

**Start person:** If a family tree on this PC has no start person yet, the program asks once when opening it: “Who should the family tree start with?” – search for the person and click. The choice becomes the family tree's default individual; change it under Person › Set as start person …
