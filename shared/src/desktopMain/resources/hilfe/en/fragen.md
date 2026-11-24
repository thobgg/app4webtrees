# Questions, errors, privacy

## Reporting questions and errors

![](desktop-hilfe.jpg)


wtWin, wtTux, wtMac and wtAnd are not official webtrees products. Please do not ask in the webtrees forum, but open an issue at **github.com/thobgg/app4webtrees/issues**. Helpful: the version from **Help › About wtWin** (with build number), Windows or Linux, and what you were doing when it happened. For the family tree on this PC also the file `php.log` (see [Family tree on this PC](hilfe:lokal)).

New versions appear at **github.com/thobgg/app4webtrees/releases**. The Windows file is not signed, so Windows warns about an unknown publisher on first start ("More info", then "Run anyway").

## What the program stores

- Server address, user name and the session cookie, **never the webtrees password**. Only the credentials of a directory protection (if entered) stay on the PC, they have to go with every request.
- From api4webtrees 1.30 a device token for signing in again quietly. It changes with every use; a stolen token thereby becomes worthless and blocks the device on the server.
- Your settings (layout, appearance, chart and list settings, colour rules, tick list of the check) on this PC.
- For the family tree on this PC: the whole tree in the folder `app4webtrees` including the login to the local webtrees.
- No analytics, no advertising, no sharing.

## Privacy in the tree

The program signs in with your webtrees account; every request runs as this user. The same rules apply as on the website: living persons, locked records and private trees are visible only if webtrees allows it. Output (charts, lists, books, PDF) contains only what you may see.

## Unencrypted on the home network

An `http://` address is accepted only on the home network (private addresses like 192.168…, names like `diskstation` or `.local`). The connection is then unencrypted, which is fine at home. To reach the tree while away, the server needs HTTPS; nas4webtrees provides the instructions.

## Phone and tablet

For Android there is **wtAnd**, the same family with the same data: tree, photos, editing, anniversaries. A QR code on the webtrees **App** page connects the phone without typing. wtAnd needs a server; the family tree on this PC is reachable only on the PC.

## Update check

Once a day, at start, the program asks `wtwin.bgg-home.de` for the current version number – a small text file, nothing else. Nothing is sent except that request: no family tree, no identifier, no usage. If a newer version exists, the status bar shows “Version 1.52 is available – Download”; a click opens the download page, × skips this version. Without network nothing happens. Switch off: **Help › Check for updates**; check by hand: **Help › Check for updates now …**. An update is only installing over the old one; settings, login and the tree on this PC stay.

## Licence

wtWin is licensed under the GPL-3, like webtrees. The family tree on this PC contains webtrees unchanged from the official release (webtrees.net).

## Full manual

With pictures and current notes: **wtwin.bgg-home.de** (button at the bottom of this window).
