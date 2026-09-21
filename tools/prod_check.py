#!/usr/bin/env python3
"""
Prueft das Modul api4webtrees gegen eine echte webtrees-Installation.

Regeln (siehe Projektnotizen):
- Zugangsdaten kommen aus testdata/prod-test.env und werden NIE ausgegeben.
- Inhalte werden nur fuer den Testbaum gelesen. Bei allen anderen Baeumen zaehlt
  nur der Statuscode; der Antwortinhalt wird verworfen (hoechstens der Fehlercode
  einer kleinen JSON-Fehlermeldung wird gelesen).
- Schreibproben an fremden Baeumen sind so gebaut, dass sie nichts anlegen koennen:
  leerer Name -> das Modul lehnt NACH der Rechtepruefung mit "name-required" ab.

Aufruf:  python3 tools/prod_check.py [--write]
"""

import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST_TREE_TITLE = 'Beispiel-Stammbaum'
MODULE = '_webtreesand-api_'

HEADERS = {
    # Ehrlicher User-Agent: wer sich als Chrome/Firefox/Safari ausgibt und noch kein Cookie hat,
    # bekommt vom BadBotBlocker erst einen "Cookie check" (406). Die App macht es genauso.
    'User-Agent': 'wtAnd/0.1 (Android; pruefskript)',
    # Der echte Server beantwortet Anfragen ohne Accept-Language mit 404.
    'Accept-Language': 'de-DE,de;q=0.9',
    'Accept': 'application/json, text/html;q=0.9, */*;q=0.8',
}


def read_env() -> dict:
    env = {}
    env_file = Path(os.environ.get('WT_ENV', ROOT / 'testdata' / 'prod-test.env'))
    for line in env_file.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in '"\'':
                value = value[1:-1]
            env[key.strip()] = value
    return env


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Client:
    def __init__(self, base_url: str):
        self.base = base_url.rstrip('/')
        self.path = urllib.parse.urlparse(self.base).path  # z. B. "/webtrees"
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), NoRedirect)
        self.csrf = ''

    def url(self, route: str, params: dict | None = None) -> str:
        query = {'route': self.path + route}
        query.update(params or {})
        return self.base + '/index.php?' + urllib.parse.urlencode(query)

    def request(self, route, params=None, data=None, headers=None):
        """-> (status, content_type, body_bytes)"""
        req = urllib.request.Request(self.url(route, params), data=data, headers={**HEADERS, **(headers or {})})
        try:
            with self.opener.open(req, timeout=30) as response:
                return response.status, response.headers.get('Content-Type', ''), response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.headers.get('Content-Type', ''), error.read()

    def api(self, action, tree=None, params=None, body=None):
        route = f'/module/{MODULE}/{action}' + (f'/{tree}' if tree else '')
        if body is None:
            return self.request(route, params)
        return self.request(route, params, json.dumps(body).encode(), {'Content-Type': 'application/json', 'X-CSRF-TOKEN': self.csrf})

    def info(self) -> dict:
        status, ctype, raw = self.api('Info')
        if status != 200 or 'json' not in ctype:
            sys.exit(f'Info antwortet nicht mit JSON (HTTP {status}, {ctype})')
        data = json.loads(raw)
        self.csrf = data['csrf']
        return data

    def login(self, user: str, password: str) -> None:
        form = urllib.parse.urlencode({'_csrf': self.csrf, 'username': user, 'password': password}).encode()
        self.request('/login', data=form, headers={'Content-Type': 'application/x-www-form-urlencoded'})


def verdict(status: int, ctype: str, raw: bytes) -> str:
    """Nur Statuscode - und bei kleinen JSON-Fehlermeldungen den Fehlercode. Inhalte werden nie gelesen."""
    if 'json' in ctype and len(raw) < 200:
        try:
            data = json.loads(raw)
            if isinstance(data, dict) and data.get('ok') is False:
                return f"abgelehnt: {data.get('error')} ({data.get('status')})"
        except ValueError:
            pass
    if status == 200:
        return f'200 ({len(raw)} Bytes, Inhalt verworfen)'
    return str(status)


def main() -> None:
    do_write = '--write' in sys.argv
    env = read_env()
    client = Client(env['WT_URL'])

    guest = client.info()
    print(f"Server: webtrees {guest['webtrees']}, Modul {guest['module']}, Basis {guest['baseUrl']}")
    guest_trees = {t['name']: t['title'] for t in guest['trees']}
    print(f"Gast sieht {len(guest_trees)} Baum/Baeume: {sorted(guest_trees.values())}")

    client.login(env['WT_USER'], env['WT_PASS'])
    info = client.info()
    user = info['user']
    print(f"\nAngemeldet: {user['loggedIn']} als '{user['userName']}', Admin: {user['isAdmin']}")
    if not user['loggedIn']:
        sys.exit('Anmeldung fehlgeschlagen - Passwort in testdata/prod-test.env pruefen.')

    print('\nBaeume fuer dieses Konto:')
    test_tree = None
    others = []
    for tree in info['trees']:
        print(f"  {tree['title']:<28} Rolle={tree['role']:<9} canEdit={tree['canEdit']!s:<5} canUpload={tree['canUpload']!s:<5} userXref={tree['userXref'] or '-'}")
        if tree['title'] == TEST_TREE_TITLE:
            test_tree = tree
        else:
            others.append(tree)

    if test_tree is None:
        sys.exit(f"Der Baum '{TEST_TREE_TITLE}' ist fuer das Konto nicht sichtbar.")

    # ── Testbaum: lesen ──────────────────────────────────────────────
    name = test_tree['name']
    print(f"\n── Testbaum '{test_tree['title']}' ({name}): lesen")
    status, _, raw = client.api('Individuals', name)
    people = json.loads(raw)
    print(f"  Individuals: HTTP {status}, {len(people['data'])} Personen auf Seite 1, nextPage={people['nextPage']}")
    root = test_tree['userXref'] or test_tree['defaultXref'] or people['data'][0]['xref']
    status, _, raw = client.api('Individual', name, {'xref': root})
    person = json.loads(raw)
    print(f"  Individual {root}: HTTP {status}, {person['person']['name']} ({person['person']['lifespan']}), "
          f"{len(person['facts'])} Ereignisse, {len(person['media'])} Medien, canEdit={person['canEdit']}")
    status, _, raw = client.api('Pedigree', name, {'xref': root, 'generations': 4})
    print(f"  Pedigree: HTTP {status}, {len(json.loads(raw)['ancestors'])} Ahnen")
    status, _, raw = client.api('Descendants', name, {'xref': root, 'generations': 3})
    print(f"  Descendants: HTTP {status}")
    if person['person']['thumb']:
        req = urllib.request.Request(person['person']['thumb'], headers=HEADERS)
        with client.opener.open(req, timeout=30) as response:
            print(f"  Thumbnail: HTTP {response.status} {response.headers.get('Content-Type')}")

    # ── Testbaum: schreiben ──────────────────────────────────────────
    if do_write:
        print(f"\n── Testbaum: schreiben (als {test_tree['role']})")
        status, _, raw = client.api('Fact', name, {'xref': root}, {'tag': 'NOTE', 'value': 'API-Test api4webtrees (kann geloescht werden)'})
        print(f"  Fact neu: HTTP {status} {raw.decode()}")
        status, _, raw = client.api('Individual', name, {'xref': root})
        notes = [f for f in json.loads(raw)['facts'] if f['tag'] == 'NOTE' and 'API-Test' in f['value']]
        if notes:
            fact_id = notes[-1]['id']
            status, _, raw = client.api('Fact', name, {'xref': root}, {'factId': fact_id, 'value': 'API-Test geaendert'})
            print(f"  Fact aendern: HTTP {status} {raw.decode()}")
            status, _, raw = client.api('Individual', name, {'xref': root})
            changed = [f for f in json.loads(raw)['facts'] if f['tag'] == 'NOTE' and f['value'] == 'API-Test geaendert']
            if changed:
                status, _, raw = client.api('DeleteFact', name, {'xref': root}, {'factId': changed[-1]['id']})
                print(f"  Fact loeschen: HTTP {status} {raw.decode()}")
        else:
            print('  (neue Notiz nicht gefunden - Aendern/Loeschen uebersprungen)')

    # ── Abschottung ──────────────────────────────────────────────────
    print('\n── Abschottung: andere Baeume (nur Statuscodes, Inhalte verworfen)')
    probe_names = {t['name'] for t in others} | set(guest_trees) - {name}
    # Auch Baeume pruefen, die das Konto gar nicht sieht - deren Kurznamen sind webtrees-Standard "tree1..n".
    probe_names |= {f'tree{i}' for i in range(1, 7)} - {name}
    for other in sorted(probe_names):
        visible = other in {t['name'] for t in info['trees']}
        read = verdict(*client.api('Individuals', other))
        write = verdict(*client.api('AddIndividual', other, None, {'relation': 'none', 'given': '', 'surname': ''}))
        print(f"  {other:<8} sichtbar={visible!s:<5} lesen: {read:<42} schreiben: {write}")

    print('\nLesart:  lesen 200 = Baum ist fuer dieses Konto lesbar (wie fuer Besucher);  404/302 = unsichtbar')
    print('         schreiben not-editor = kein Schreibrecht;  name-required = Schreibrecht VORHANDEN (nichts angelegt)')


if __name__ == '__main__':
    main()
