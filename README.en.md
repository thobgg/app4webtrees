# wtAnd

[Deutsch](README.md) · **English**

<p align="center">
  <img src="docs/icon/icon-512.png" alt="wtAnd logo" width="112">
</p>

<p align="center">
  <a href="https://github.com/thobgg/app4webtrees/releases/latest"><img src="https://img.shields.io/badge/Android-wtAnd%20APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android: wtAnd (APK)"></a>
  <a href="https://github.com/thobgg/app4webtrees/releases/latest"><img src="https://img.shields.io/badge/Linux-wtTux%20.deb-FCC624?style=for-the-badge&logo=linux&logoColor=black" alt="Linux: wtTux (.deb)"></a>
  <a href="https://github.com/thobgg/app4webtrees/releases/latest"><img src="https://img.shields.io/badge/Windows-wtWin%20.exe-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows: wtWin (.exe)"></a>
  <a href="https://github.com/thobgg/app4webtrees/releases/latest"><img src="https://img.shields.io/badge/macOS-wtMac%20.dmg%20(Test)-A2AAAD?style=for-the-badge&logo=apple&logoColor=white" alt="macOS: wtMac (.dmg), for testing"></a>
</p>

<p align="center"><b>New in 1.38: directory protection.</b> If your web server protects the whole site with its own user name and password (.htaccess, the browser shows a small sign-in box before webtrees – e.g. to keep data-harvesting bots out), enter those credentials on the address screen under “directory protection”. wtAnd, wtWin and wtTux send them with every request to exactly that server; the webtrees sign-in follows separately. Nothing changes in the module.</p>

<p align="center"><b>New in 1.37: tasks, bookmarks, changes.</b> Research tasks as in webtrees (View › Research tasks, also from the plausibility check), bookmarks in webtrees’ favourites with favourites for the whole tree, the tree’s recent changes, arrows to reorder partners and children, and a compact merge dialog. Needs api4webtrees 1.18.0 – with it the interface is complete.</p>

<p align="center"><b>New in 1.36: merging individuals.</b> The plausibility check finds duplicates (same name and one identical event date, or similar); Individual › Merge individuals shows both side by side and you choose what stays – all links move along. Every merge is logged and can be undone, even days later. Needs api4webtrees 1.17.1; tree managers only. <b>New family view:</b> ancestors of both partners over up to four generations, couple cards, children with spouses and grandchildren, lines, fitted to the window height.</p>

<p align="center">
  <img src="docs/screenshots/desktop-familienansicht.jpg" alt="Family view: Georg Mohwinkel and Catharine Schulze with grandparents, parents, siblings, status line, five children, a spouse and grandchildren" width="100%">
  <br><b>Family view</b> (new in 1.36) – ancestors at the top, the couple in the middle, children with spouses and grandchildren at the bottom, fitted to the window height.
</p>

<p align="center">
  <img src="docs/screenshots/desktop-zusammenfuehren.jpg" alt="Merging individuals: Johann Heinrich Falkenrath stays, the second copy is absorbed; one residence is added, below the links and a further pair" width="100%">
  <br><b>Merging individuals</b> (from 1.36) – who stays, who is absorbed, and only what is added from the second individual; below, what will point to the remaining individual and further pairs. “Show all events” opens the full side-by-side comparison.
</p>

<p align="center"><b>New in 1.35: houses and farms.</b> Buildings are places of their own (GEDCOM-L) – in the place manager with their history (fire, rebuilding, residents and owners in order of time), in the family book as a house section with cross-references. To try it out: <a href="https://github.com/thobgg/falkenrath-demo-tree">demo tree 1.4</a>.</p>
<p align="center"><b>New in 1.33: five languages.</b> wtWin, wtTux and wtAnd now also speak French, Dutch and Spanish besides German and English – switch under View › Language (on the phone in the menu), with help and plausibility check in every language.</p>

<p align="center"><b>New in 1.31/1.32: godparents and witnesses.</b> Below baptism and marriage the godparents or witnesses appear – linked ones clickable, free-text ones as text (also from the GEDCOM-L fields _GODP/_WITN). Every person shows where they were a godparent or witness themselves. From 1.32 they can also be entered in the person sheet – from the tree or without a record, as in the church register – together with the type of marriage. Needs <a href="https://github.com/thobgg/api4webtrees/releases">api4webtrees 1.12</a> on the server.</p>

<p align="center"><b>New in 1.26: the family tree on your PC – no server needed.</b> Install wtWin (wtTux on Linux), choose the GEDCOM file from your previous program – done. No server, no password, no internet; webtrees runs invisibly in the background.</p>

<p align="center">
  <img src="docs/screenshots/windows-navigator.jpg" alt="wtWin on Windows: navigator with Heinrich Falkenrath, children and four generations of ancestors" width="100%">
  <br><b>wtWin on Windows</b> – the same family tree as in wtAnd, laid out like a classic genealogy program.
</p>

<p align="center">
  <img src="docs/screenshots/desktop-baum-vollbild.jpg" alt="wtTux: tree in full screen, six generations around Frieda Behnke" width="100%">
  <br><b>Full screen</b> (from 1.28) – only the tree, up to seven generations; Esc returns.
</p>

| <img src="docs/screenshots/windows-personenblatt.jpg" alt="wtWin: person sheet of Frieda Behnke" width="100%"> | <img src="docs/screenshots/desktop-baum-mittelpunkt.jpg" alt="wtTux: tree-centred layout with person list, hourglass tree and person panel" width="100%"> |
| :-: | :-: |
| **Person sheet** – events as a table, the family beside them, print and PDF | **“Tree in the centre” layout** (View › Layout) – person list on the left, the tree as an hourglass in the middle, the person panel on the right |

<p align="center">
  <img src="docs/screenshots/desktop-paten.jpg" alt="Timeline of Heinrich Falkenrath: godparents at the christening, witnesses at the marriage, below his own godparent and witness roles" width="100%">
  <br><b>Godparents and witnesses</b> (from 1.31) – below baptism and marriage, linked godparents lead to the person, ⓘ shows the note about the godparent, the icon next to it the source; below, where the person was a godparent or witness. The same in the phone timeline.
</p>

<p align="center">
  <img src="docs/screenshots/desktop-paten-dialog.jpg" alt="Edit godparents dialog: a linked godmother with a note, a godparent without a record, buttons to add a person from the tree or without a record" width="70%">
  <br><b>Entering godparents</b> (from 1.32) – in the person sheet at baptism or marriage: a person from the tree or without a record, order, role and note. Stored as webtrees does, people without a record as GEDCOM-L _GODP/_WITN.
</p>

<p align="center">
  <img src="docs/screenshots/desktop-tafel-fenster.jpg" alt="wtTux: chart window with ancestor circle over seven generations" width="100%">
  <br><b>Charts</b> – choose a chart type, style it (style, box shape, background, frame, colours, legend), set its content; the preview is the finished sheet, to print, as PDF, on A4 sheets to glue, for the plotter or as a picture.
</p>

| <img src="docs/screenshots/tafel-ahnentafel.jpg" alt="Ancestor chart of Jonas Falkenrath coloured by grandparent lines with legend" width="100%"> | <img src="docs/screenshots/tafel-stammtafel.jpg" alt="Descendant chart of Johann Heinrich Falkenrath with oval boxes" width="100%"> |
| :-: | :-: |
| **Ancestor chart** – ancestors, Kekulé numbers, colours by line | **Descendant chart** – all descendants on one sheet |
| <img src="docs/screenshots/tafel-ahnenkreis.jpg" alt="Ancestor circle of Jonas Falkenrath over seven generations" width="100%"> | <img src="docs/screenshots/tafel-zeitleiste.jpg" alt="Timeline of the ancestors of Jonas Falkenrath with historical events" width="100%"> |
| **Ancestor circle** – seven generations in a circle | **Timeline** – who lived when |
| <img src="docs/screenshots/tafel-verwandtschaftsweg.jpg" alt="Relationship path between Jonas Falkenrath and Florian Ahlers" width="100%"> | <img src="docs/screenshots/tafel-montageplan.jpg" alt="Assembly plan of an ancestor chart on six A4 sheets" width="100%"> |
| **Relationship path** – how two people are related | **Large print** – sheets to glue or plotter |

**[All 15 chart types, lists and books with examples →](docs/GALERIE.en.md)** Ancestor chart (also on pages), fan chart, ancestor circle, timeline, paternal line, maternal line, oldest ancestor, descendant chart (also on pages), descendants of the grandparents, hourglass, couple hourglass, relationship chart, relationship path.

<p align="center">
  <img src="docs/screenshots/desktop-pruefung.jpg" alt="wtTux: plausibility check window with preset and findings in the demo tree" width="100%">
  <br><b>Plausibility check</b> (from 1.22, extended in 1.23) – 61 rules check the whole tree: death before birth, mother too young, godparent already dead, possible duplicates, place variants, own ancestor … presets from strict to lenient, adjustable limits; tick off checked findings, edit the event straight from the finding, print and PDF.
</p>

<p align="center">
  <img src="docs/screenshots/desktop-hilfe.jpg" alt="wtTux: help window with table of contents, search for “Plotter” and highlighted hits in the Charts chapter" width="100%">
  <br><b>Help inside the program</b> (from 1.27) – F1 opens twelve chapters with search, in every window the matching one; technical terms in the settings explain themselves on mouse-over:
</p>

<p align="center"><img src="docs/screenshots/desktop-tooltip.jpg" alt="wtTux: chart window with the explanation of “Name bearers only” on mouse-over" width="100%"></p>

<p align="center"><sub>All pictures: fictitious demo tree <a href="https://github.com/thobgg/falkenrath-demo-tree">Familie Falkenrath</a> (CC0), photos of unknown people from the Rijksmuseum Amsterdam (CC0).</sub></p>

**Android:** the APK is signed. To install outside the Play Store, Android asks once to allow your browser to install apps.  
**Linux:** install the package with `sudo apt install ./wttux_…_amd64.deb`.  
**Windows:** run `wtWin-….exe`. The installer first asks for the language (English, German, French, Dutch, Spanish) and installs for the current user without admin rights; from 1.42 it also replaces an older version by itself. The file is not signed, so Windows warns about an unknown publisher on first start: choose "More info", then "Run anyway".  
**macOS (for testing):** `wtMac-…-arm64.dmg` for Apple silicon, `…-x64.dmg` for Intel Macs; open it and drag wtMac to Applications. The file is not signed: on first start choose System Settings › Privacy & Security › "Open Anyway". Not yet tested on a real Mac – feedback welcome as an issue.

**With or without a server:** wtWin and wtTux connect to an existing webtrees, which needs the [api4webtrees](https://github.com/thobgg/api4webtrees) module. Or they create the family tree **on this PC**, empty or from a GEDCOM file exported from your previous program (from 1.26). For this they include webtrees (unchanged from the [official release](https://github.com/fisharebest/webtrees/releases), GPL-3) and PHP – no server, no password, no internet needed. wtAnd and wtMac need a server.

<p align="center"><img src="docs/screenshots/desktop-start.jpg" alt="Start screen: connect to webtrees on the left, create a family tree on this PC or import GEDCOM on the right" width="100%"><br><b>First start</b> – connect to a webtrees server on the left, or keep the family tree on your PC on the right (screenshot in German).</p>

wtAnd, wtWin, wtTux and wtMac are not official webtrees products; questions and bug reports please as an [issue](https://github.com/thobgg/app4webtrees/issues).

A native Android app for [webtrees](https://webtrees.net/) – view **and edit** your family tree on phone and tablet,
with your own data on your own server.

That puts the whole family tree in your pocket: look up what is known, record something at the archive or the cemetery,
and change data. The detailed work, such as the source manager, charts and books, stays at the PC (wtWin, wtTux). What
comes from the app arrives as a pending change in the same webtrees installation, under its rights, moderation and
rules.

| Tree | Timeline | Archive |
| - | - | - |
| <img src="docs/screenshots/handy-baum.png" alt="Tree on a phone" width="250"> | <img src="docs/screenshots/handy-zeitleiste.png" alt="Profile with timeline" width="250"> | <img src="docs/screenshots/handy-archiv.png" alt="Archive of the Sammlungen module" width="250"> |

<sub>**wtAnd on the phone.** The phone pictures show the entirely fictional demo tree “Familie Falkenrath” (see [falkenrath-demo-tree](https://github.com/thobgg/falkenrath-demo-tree)). The screenshots are in German; the app also speaks English.</sub>

## Your data stays yours

- The tree lives on **your** webtrees server; the app is just a window onto it. No account with a provider, no
  subscription, no ads, nothing that holds your data hostage.
- Photo details – description, date, people – are written **into the image file** (EXIF/XMP), not into a database
  only one company can read. The details travel with the photo wherever it goes.
- Files in the archive don't have to hang on anyone. Thirty pictures of an ancestor, three of which belong on their
  record – the other twenty-seven are the archive.
- Open source under the GPL, like webtrees itself.

## Features

- **The tree is the centre:** hourglass view with ancestors, partners, children and grandchildren – plus, as a
  family view usually does, the siblings of the focus person and of their ancestors with partners, and the cousins
  (both can be switched off);
  pan and zoom freely, expand branches upwards, make any person the focus. Zooming out, a card shows less rather
  than smaller: first without portrait and years, then just the given name, finally a box in the sex colour, with
  the text staying readable
- **Godparents and witnesses** (from 1.31, with api4webtrees 1.11): below baptism and marriage, tap a linked one to open
  the person; every person also shows “godparent at …” and “witness at …”. Civil and religious marriage separately.
  Entering them and choosing the type of marriage on the PC with wtWin/wtTux (from 1.32, with api4webtrees 1.12)
- **Profile:** life as a timeline (including marriage and births of children), relationship to yourself
  (“paternal grandfather”), photos, family, map of the stations of a life (OpenStreetMap)
- **Editing:** add, change and delete events – also marriages and other family events; add relatives right in the tree
  with the “+” on every card; remove links, delete individuals. Dates are picked (exact, about, before, after,
  between · day, month, year), places are suggested from the tree while typing
- **Photos:** take or choose a picture and attach it to a person – it is shrunk to fit the server's upload limit;
  photo overview of the whole tree. A tap opens the viewer: swipe, pinch, double-tap
- **Archive:** if the server runs the [Sammlungen](https://github.com/thobgg/webtrees-sammlungen) module (1.6 or
  newer), the “Photos” section also shows its archive – folder collections, thematic collections, collections by media
  type, and above all the pictures that hang on nobody. Every tile says how the picture relates to the tree: linked to
  people, a media object without a person, or a free file. Editing stays in the module
- **Capture:** on the road, photograph a picture from the drawer, pick a folder, add description, date and people (the
  app suggests names from the tree), done – it lands as a file in the archive with the details as EXIF inside the
  file, without hanging on a person (Sammlungen module 1.7 or newer). Managers edit the details of a picture
  right in the viewer, from the archive, the tree and a profile
- **Documents:** PDFs from the archive, the tree and a profile open in the app's own viewer, page by page, without a
  browser window
- **Anniversaries:** upcoming birthdays, wedding days and days of death, with an optional daily reminder
- **Moderation:** moderators accept or reject pending changes in the app
- **Compact on phones, comprehensive on tablets**
- German, English, French, Dutch and Spanish, switchable in the app (otherwise like the device; any other language gets English). Labels from the server arrive in the language of the app

Whatever is not native (yet) opens as the webtrees page in the same session.

## Requirement: the webtrees module

webtrees has no interface for apps. The app therefore talks to the module
**[api4webtrees](https://github.com/thobgg/api4webtrees)**, which you copy into `modules_v4/` of your own
webtrees server (2.2.x). The webtrees core stays untouched. Godparents and witnesses are shown from api4webtrees 1.11
and can be entered from 1.12; with an older module everything else works as before.

Optional: with the **[Sammlungen](https://github.com/thobgg/webtrees-sammlungen)** module, version 1.6 or newer, the
app also shows the archive. Without it, only that tab is missing.

## Connecting without typing

On the **“App”** page in webtrees (api4webtrees 1.9.4 or later, included in [nas4webtrees](https://github.com/thobgg/nas4webtrees))
one click connects the program to your own account – nobody has to type the address or password:

- **wtWin/wtTux (1.21 or later):** install and start the program, then click **“Connect with wtWin”** in the browser.
  The program takes the connect link from the clipboard or gets it straight from the browser (`wtwin://`, `wttux://` –
  on Windows the installer registers this from 1.42, otherwise the program registers itself at first start, without
  admin rights), asks once and opens the tree.
- **wtAnd:** tap “Connect now” on the phone, or scan the QR code with the phone camera at the PC.

The link carries a one-time code that is valid for 10 minutes and exactly once; the password never reaches the device.

**Directory protection (1.38 or later):** if the web server protects the whole site with its own user name and password
(.htaccess/.htpasswd, the browser shows a small sign-in box before webtrees, e.g. to keep data-harvesting bots out), enter
those credentials on the address screen under **“directory protection”**. The app sends them with every request to
exactly that server, including pictures and the anniversary reminder; the webtrees sign-in follows separately. The
fields open by themselves when the server asks for such a sign-in. This does not help with SSO services (Authelia,
Authentik, oauth2-proxy, Cloudflare Access); there the operator has to let the app's requests through, see the
api4webtrees README.

## Privacy

- The app signs in with your normal webtrees account. Every request runs as that user – the same privacy rules apply as
  on the website (living individuals, restricted facts, private trees).
- Changes go straight to webtrees: with “automatically accept changes” they are final, otherwise they wait for a
  moderator.
- Stored on the device: server address, user name and the session cookie – **never the webtrees password**. Only the
  credentials of a directory protection (if entered) stay on the device, they have to go with every request. No cloud backup of
  the app data, no analytics, no ads, no Google services.
- Permissions: internet. For the optional daily anniversary reminder: notifications (requested only when you switch it
  on) plus the usual rights of Android's job scheduler (network state, start after reboot, wake lock, foreground
  service). “Take photo” needs no camera permission – the device's camera app takes the picture. No access to contacts,
  location or files.

## Pictures

**Tree.** Hourglass around the focus person, siblings and cousins included. Zooming out, a card shows less rather than
smaller, and the text stays readable.

| close | names | given names | overview |
| - | - | - | - |
| <img src="docs/screenshots/handy-baum.png" alt="Tree close up" width="190"> | <img src="docs/screenshots/handy-baum-namen.png" alt="Tree with names" width="190"> | <img src="docs/screenshots/handy-baum-rufnamen.png" alt="Tree with given names" width="190"> | <img src="docs/screenshots/handy-baum-uebersicht.png" alt="Tree overview" width="190"> |

**Person.** Life as a timeline with marriage, the births and marriages of the children and the age at death; family
with parents, siblings, partners and children; map of the stations of a life.

| Profile | Timeline | Family | Map |
| - | - | - | - |
| <img src="docs/screenshots/handy-profil.png" alt="Profile" width="190"> | <img src="docs/screenshots/handy-zeitleiste.png" alt="Timeline" width="190"> | <img src="docs/screenshots/handy-familie.png" alt="Family" width="190"> | <img src="docs/screenshots/handy-karte.png" alt="Map" width="190"> |

**Photos and archive.** The tree's photos, dense on request; the archive of the Sammlungen module with folder
collections, thematic collections and the free holdings; the viewer with pinch zoom and caption editing; PDFs page by page.

| Photos | dense | Collection | Viewer |
| - | - | - | - |
| <img src="docs/screenshots/handy-fotos.png" alt="Photos of the tree" width="190"> | <img src="docs/screenshots/handy-fotos-dicht.png" alt="dense grid" width="190"> | <img src="docs/screenshots/handy-sammlung.png" alt="Collection with badges" width="190"> | <img src="docs/screenshots/handy-betrachter.png" alt="Viewer" width="190"> |

**Start, search, documents, tablet.**

| Start | Search | PDF | Tablet |
| - | - | - | - |
| <img src="docs/screenshots/handy-start.png" alt="Start with anniversaries" width="190"> | <img src="docs/screenshots/handy-suche.png" alt="Search" width="190"> | <img src="docs/screenshots/handy-pdf.png" alt="PDF viewer" width="190"> | <img src="docs/screenshots/tablet-baum.png" alt="Tree and profile side by side" width="190"> |

## Build

```bash
./gradlew :app:assembleDebug
```

`minSdk` 26, `compileSdk` 36, Kotlin and Jetpack Compose; the build needs **JDK 21**.

Where to start reading: `MainActivity` shows `ui/MainScreen.kt` (screen choice, navigation, menu). The state lives in
`ui/UiState.kt`, held by the view model `ui/AppViewModel.kt`; its actions are grouped by area in `SessionActions`,
`TreeActions`, `EditActions`, `PhotoActions` and `AnniversaryActions`. Each section has its own file (`TreeSection`, `HomeSection`,
`SearchSection`, `PhotosSection`); the profile consists of `ProfilePanel`, `Timeline`, `Relatives` and `LifeMap`.
`api/` talks to the module, `ui/tree/` lays out and draws the tree, `data/` prepares dates and photos.

## License

[GPL-3.0](LICENSE), like webtrees. The [demo tree](https://github.com/thobgg/falkenrath-demo-tree) is CC0.
