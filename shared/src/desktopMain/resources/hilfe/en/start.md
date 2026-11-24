# Getting started

wtWin (wtTux on Linux) shows and edits a webtrees family tree like a classic desktop genealogy program: menu bar, navigator, person sheet, charts, lists and books. The data lives either on a webtrees server (NAS or web host) or directly on this PC.

![](desktop-start.jpg)


On first start, both ways stand side by side.

## Connect to webtrees

Requirement: webtrees 2.2 with the module **api4webtrees**. Whoever runs the server installs the module; the nas4webtrees package includes it.

**Easiest, no typing:** sign in to webtrees in your browser, open the **App** menu and click **Connect with wtWin**. The program picks up the connect link (from the clipboard or directly from the browser), asks once and opens the tree. No address, no password. The link is valid for ten minutes and exactly once.

**By hand:**

1. Enter the address of your webtrees site as shown in the browser. The program trims the rest itself. If a suitable address is on the clipboard, it is suggested.
2. Click **Connect**.
3. Sign in with user name (or e-mail) and password. **View without signing in** shows only what visitors see on the website.

> **Directory protection:** if the browser shows a small sign-in box of the web server (.htaccess) before webtrees, open “Server asks for a user name and password before webtrees” on the address screen and enter those credentials there. The program sends them with every request to this server; you then sign in as usual. When the server asks for such a sign-in, the fields open by themselves.

> **Staying signed in:** many servers drop the webtrees session after a break of about 24 minutes. From api4webtrees 1.30 the program receives a device token when you sign in and uses it to sign in again quietly; it asks for the password only when the token has not been used for 90 days, the password was changed or the device was disconnected. The connected devices are listed in webtrees in the menu **App**, where each can be disconnected. **Sign out** disconnects the device as well.

You sign in with your normal webtrees account. The same rights apply as on the website: what you cannot see there, you cannot see here; what you may edit there, you may edit here.

> Unencrypted addresses (`http://`) are accepted only on your home network, e.g. `http://192.168.178.73:8095`. Away from home, the server needs HTTPS.

## Family tree on this PC

On the right of the start screen: enter a name and click **Create family tree**, or **Import from GEDCOM file …** to move over from another program. From then on you work without a server, password or internet. See [Family tree on this PC](hilfe:lokal).

## Switching trees or servers

- If the server has several trees, switch under **File › Switch tree**.
- To another server: **File › Sign out**, then **Other address**. Or click **Connect with wtWin** on the **App** page of the other server; this also works while you are signed in.

## Language

The program follows the system language (German, English, French, Dutch or Spanish, otherwise English). Choose a fixed language under **View › Language**. Labels that come from the server (event names, relationships) appear in the program's language.

## Next

- [The main window](hilfe:hauptfenster): navigator, toolbar, view
- [Person sheet and editing](hilfe:person)
- [Charts](hilfe:tafeln), [Lists](hilfe:listen), [Books](hilfe:buecher)
- [Keyboard shortcuts](hilfe:tasten)
