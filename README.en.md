# wtAnd

[Deutsch](README.md) · **English**

### [⬇ Download the APK](https://github.com/thobgg/wtAnd/releases/latest)

Signed. To install outside the Play Store, Android asks once to allow your browser to install apps.
Your webtrees server needs the [api4webtrees](https://github.com/thobgg/api4webtrees) module.

A native Android app for [webtrees](https://webtrees.net/) – view **and edit** your family tree on phone and tablet,
with your own data on your own server.

The app brings two worlds together: the person who maintains the tree meticulously at the PC, and the family who want
to look into it on a phone or tablet and contribute photos and hints. The detailed work stays at the PC. What comes from
the app arrives as a pending change in the same webtrees installation, under its rights, moderation and rules.

| Tablet | Phone |
| - | - |
| ![Tree and profile side by side](docs/screenshots/tablet-baum.png) | ![Tree on a phone](docs/screenshots/handy-baum.png) |

<sub>All pictures show the entirely fictional demo tree “Familie Falkenrath” (see [demo-tree/](demo-tree/)). The screenshots are in German; the app also speaks English.</sub>

## Features

- **The tree is the centre:** hourglass view with ancestors, partners, children and grandchildren – plus, as a
  family view usually does, the siblings of the focus person and of their ancestors with partners, and the cousins
  (both can be switched off);
  pan and zoom freely, expand branches upwards, make any person the focus
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
  people, a media object without a person, or a free file. Viewing only; editing stays in the module
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
