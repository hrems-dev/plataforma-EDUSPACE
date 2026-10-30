#!/usr/bin/env python3
"""Registra en el cliente `eduspace-web` de Keycloak las direcciones desde las que se abre la web (localhost y las IP de este equipo).

Sin esto, Keycloak rechaza el regreso del login («Invalid parameter: redirect_uri») cuando la web se abre por http://<IP>:4200.
Solo DEV. Se puede repetir; `modo.sh keycloak` lo ejecuta solo.
"""
import json, os, subprocess, urllib.request, urllib.parse
KC = os.environ.get('KEYCLOAK_URL', 'http://localhost:18090'); REALM = 'eduspace'
ips = sorted(set(subprocess.run("ip -4 -o addr show scope global | awk '{print $4}' | cut -d/ -f1", shell=True, capture_output=True, text=True).stdout.split()))
origenes = ['http://localhost:4200', 'http://127.0.0.1:4200'] + [f'http://{ip}:4200' for ip in ips]
def req(m, p, tok=None, body=None, form=None):
    h = {}; data = None
    if form: data = urllib.parse.urlencode(form).encode()
    elif body is not None: data = json.dumps(body).encode(); h['Content-Type'] = 'application/json'
    if tok: h['Authorization'] = 'Bearer ' + tok
    with urllib.request.urlopen(urllib.request.Request(KC + p, data=data, headers=h, method=m), timeout=20) as x:
        t = x.read(); return json.loads(t) if t[:1] in (b'{', b'[') else None
tok = req('POST', '/realms/master/protocol/openid-connect/token', form={'grant_type': 'password', 'client_id': 'admin-cli', 'username': 'admin', 'password': 'admin'})['access_token']
c = req('GET', f'/admin/realms/{REALM}/clients?clientId=eduspace-web', tok)[0]
c['redirectUris'] = [o + '/*' for o in origenes]; c['webOrigins'] = origenes
c.setdefault('attributes', {})['post.logout.redirect.uris'] = '##'.join(o + '/*' for o in origenes)
req('PUT', f'/admin/realms/{REALM}/clients/{c["id"]}', tok, c)
print('Orígenes permitidos en Keycloak:', ', '.join(origenes))
