#!/usr/bin/env python3
"""Crea en Keycloak los usuarios de la demostración, con el mismo `usuarioId` que tienen en EduSpace (auth-ms).

Así las cuentas jorge.paredes / lucia.mendoza / carmen.rios funcionan también cuando el sistema corre con Keycloak
(`tools/e2e/modo.sh keycloak-form`): el token trae su usuarioId y los servicios las reconocen como docente, alumna y apoderada.
Se puede repetir: si el usuario ya existe, se actualiza (clave, roles y usuarioId).  Clave de todos: Expo12345.
"""
import json, os, sys, urllib.request, urllib.parse, urllib.error

KC = os.environ.get('KEYCLOAK_URL', 'http://localhost:18090')
REALM = 'eduspace'
CLAVE = 'Expo12345'
DEMO = os.environ.get('USUARIOS_DEMO', 'jorge.paredes,lucia.mendoza,carmen.rios').split(',')

def req(m, url, tok=None, body=None, form=None):
    h = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode(); h['Content-Type'] = 'application/x-www-form-urlencoded'
    elif body is not None:
        data = json.dumps(body).encode(); h['Content-Type'] = 'application/json'
    if tok: h['Authorization'] = 'Bearer ' + tok
    r = urllib.request.Request(url, data=data, headers=h, method=m)
    try:
        with urllib.request.urlopen(r, timeout=30) as x:
            t = x.read(); return x.status, (json.loads(t) if t[:1] in (b'{', b'[') else None), x.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300], e.headers

# 1) quién es quién en EduSpace (id, nombres y roles): se lee de la base de auth-ms, porque con Keycloak activo auth-ms ya no acepta los tokens del realm
import subprocess
CT = os.environ.get('CONTENEDORES', 'podman')
lista_sql = ",".join("'" + n + "'" for n in DEMO)
sql = ("select u.id, u.username, u.nombres, u.apellidos, u.email, coalesce(string_agg(r.nombre, ','), '') from usuarios u "
       "left join usuario_roles ur on ur.usuario_id = u.id left join roles r on r.id = ur.rol_id "
       f"where u.username in ({lista_sql}) group by u.id")
try:
    out = subprocess.run([CT, 'exec', 'e2e-pg-auth', 'psql', '-U', 'eduspace', '-d', 'auth_db', '-tA', '-F', '|', '-c', sql], capture_output=True, text=True, check=True).stdout
except Exception as e:
    sys.exit(f'No se pudo leer la base de auth-ms (contenedor e2e-pg-auth con {CT}): {e}')
usuarios = {}
for linea in out.strip().splitlines():
    i, nombre, nombres, apellidos, email, roles = linea.split('|')
    usuarios[nombre] = {'id': int(i), 'nombres': nombres, 'apellidos': apellidos, 'email': email, 'roles': [r for r in roles.split(',') if r]}

# 2) Keycloak (administración)
s, d, _ = req('POST', f'{KC}/realms/master/protocol/openid-connect/token', form={'grant_type': 'password', 'client_id': 'admin-cli', 'username': 'admin', 'password': 'admin'})
adm = d['access_token']
for nombre in DEMO:
    u = usuarios.get(nombre)
    if not u:
        print(f'  ✘ {nombre}: no existe en EduSpace'); continue
    cuerpo = {'username': nombre, 'enabled': True, 'emailVerified': True, 'firstName': u.get('nombres') or nombre, 'lastName': u.get('apellidos') or '-',
              'email': u.get('email') or f'{nombre}@eduspace.com', 'attributes': {'usuarioId': [str(u['id'])]},
              'credentials': [{'type': 'password', 'value': CLAVE, 'temporary': False}], 'requiredActions': []}
    s, lista, _ = req('GET', f'{KC}/admin/realms/{REALM}/users?username={urllib.parse.quote(nombre)}&exact=true', adm)
    if lista:
        kid = lista[0]['id']
        req('PUT', f'{KC}/admin/realms/{REALM}/users/{kid}', adm, {k: v for k, v in cuerpo.items() if k != 'credentials'})
        req('PUT', f'{KC}/admin/realms/{REALM}/users/{kid}/reset-password', adm, {'type': 'password', 'value': CLAVE, 'temporary': False})
        accion = 'actualizado'
    else:
        s, r, h = req('POST', f'{KC}/admin/realms/{REALM}/users', adm, cuerpo)
        if s != 201: print(f'  ✘ {nombre}: {s} {r}'); continue
        kid = h['Location'].rsplit('/', 1)[1]; accion = 'creado'
    for rol in u.get('roles', []):
        s, r, _ = req('GET', f'{KC}/admin/realms/{REALM}/roles/{rol}', adm)
        if s == 200: req('POST', f'{KC}/admin/realms/{REALM}/users/{kid}/role-mappings/realm', adm, [r])
    print(f"  ✔ {nombre} {accion} en Keycloak (usuarioId {u['id']}, roles {u.get('roles')})")
print(f'Clave de las cuentas de la demo en Keycloak: {CLAVE}')
