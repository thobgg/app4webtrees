# wtAnd

[Deutsch](README.md) · **English**

### [⬇ Download the APK](https://github.com/thobgg/app4webtrees/releases/latest)

**Linux:** the desktop program **wtTux** comes as `wttux_…_amd64.deb` in the [same release](https://github.com/thobgg/app4webtrees/releases/latest); install with `sudo apt install ./wttux_…_amd64.deb`. Windows (wtWin) will follow.

Signed. To install outside the Play Store, Android asks once to allow your browser to install apps.
Your webtrees server needs the [api4webtrees](https://github.com/thobgg/api4webtrees) module.

A native Android app for [webtrees](https://webtrees.net/) – view **and edit** your family tree on phone and tablet,
with your own data on your own server.

The app brings two worlds together: the person who maintains the tree meticulously at the PC, and the family who want
to look into it on a phone or tablet and contribute photos and hints. The detailed work stays at the PC. What comes from
the app arrives as a pending change in the same webtrees installation, under its rights, moderation and rules.

| Tree | Timeline | Archive |
| - | - | - |
| <img src="docs/screenshots/handy-baum.png" alt="Tree on a phone" width="250"> | <img src="docs/screenshots/handy-zeitleiste.png" alt="Profile with timeline" width="250"> | <img src="docs/screenshots/handy-archiv.png" alt="Archive of the Sammlungen module" width="250"> |

<sub>All pictures show the entirely fictional demo tree “Familie Falkenrath” (see [demo-tree/](demo-tree/)). The screenshots are in German; the app also speaks English.</sub>

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
- German and English; devices set to any other language get English. Labels from the server arrive in the language of the app

Whatever is not native (yet) opens as the webtrees page in the same session.

## Requirement: the webtrees module

webtrees has no interface for apps. The app therefore talks to the module
**[api4webtrees](https://github.com/thobgg/api4webtrees)**, which you copy into `modules_v4/` of your own
webtrees server (2.2.x). The webtrees core stays untouched.

Optional: with the **[Sammlungen](https://github.com/thobgg/webtrees-sammlungen)** module, version 1.6 or newer, the
app also shows the archive. Without it, only that tab is missing.

## Privacy

- The app signs in with your normal webtrees account. Every request runs as that user – the same privacy rules apply as
  on the website (living individuals, restricted facts, private trees).
- Changes go straight to webtrees: with “automatically accept changes” they are final, otherwise they wait for a
  moderator.
- Stored on the device: server address, user name and the session cookie – **never the password**. No cloud backup of
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

[GPL-3.0](LICENSE), like webtrees. The demo tree in `demo-tree/` is CC0.
