"""Cliente mínimo para sembrar y probar EduSpace por el Gateway (solo biblioteca estándar).

Modo de autenticación (variable AUTH_MODE):
  keycloak (por defecto si KEYCLOAK_URL responde): tokens del realm eduspace con el cliente eduspace-e2e.
  eduspace: tokens de auth-ms (admin/secretaria/cajero de las semillas; los demás roles los crea la semilla).
"""
import json, os, time, urllib.parse, urllib.request, urllib.error, uuid

GW = os.environ.get('GATEWAY', 'http://localhost:18080')
AUTH = os.environ.get('AUTH_URL', 'http://localhost:8090')
KC = os.environ.get('KEYCLOAK_URL', 'http://localhost:18090')
MODO = os.environ.get('AUTH_MODE', 'keycloak')

CLAVES = {  # usuarios de DEV (los mismos de tools/e2e/README.md y del realm de Keycloak)
    'admin': 'admin123', 'secretaria': 'secre123', 'cajero': 'cajero123',
    'docente': 'Clave12345', 'apoderado': 'Clave12345', 'alumno': 'Clave12345',
}
_cache = {}


def _post_form(url, datos):
    r = urllib.request.Request(url, data=urllib.parse.urlencode(datos).encode())
    return json.load(urllib.request.urlopen(r))


def token(usuario):
    if usuario in _cache:
        return _cache[usuario]
    if MODO == 'keycloak':
        t = _post_form(f'{KC}/realms/eduspace/protocol/openid-connect/token',
                       {'grant_type': 'password', 'client_id': 'eduspace-e2e', 'username': usuario, 'password': CLAVES[usuario]})['access_token']
    else:
        r = urllib.request.Request(f'{AUTH}/api/v1/auth/login', data=json.dumps({'username': usuario, 'password': CLAVES[usuario]}).encode(),
                                   headers={'Content-Type': 'application/json'})
        t = json.load(urllib.request.urlopen(r))['access_token']
    _cache[usuario] = t
    return t


def call(metodo, ruta, usuario, cuerpo=None, crudo=None, ctype='application/json'):
    """Una llamada; si el Gateway aún no encuentra el servicio (0, 502, 503, 504) o limita el ritmo (429) la repite unas veces."""
    for intento in range(6):
        s, d = _llamar(metodo, ruta, usuario, cuerpo, crudo, ctype)
        if s not in (0, 429, 502, 503, 504) or intento == 5:
            return s, d
        time.sleep(10 if s == 429 else 3)


def _llamar(metodo, ruta, usuario, cuerpo=None, crudo=None, ctype='application/json'):
    cab = {'Authorization': 'Bearer ' + token(usuario)}
    datos = None
    if crudo is not None:
        datos, cab['Content-Type'] = crudo, ctype
    elif cuerpo is not None:
        datos, cab['Content-Type'] = json.dumps(cuerpo).encode(), 'application/json'
    r = urllib.request.Request(GW + ruta, data=datos, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(r) as x:
            t = x.read().decode()
            return x.status, (json.loads(t) if t.strip()[:1] in ('{', '[') else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except ValueError:
            return e.code, t
    except urllib.error.URLError as e:  # el servicio todavía no responde
        return 0, str(e.reason)


def esperar_backend(segundos=300):
    """Espera a que el Gateway y los servicios respondan (Eureka tarda unos segundos en propagar las rutas)."""
    rutas = ['/api/v1/planificacion/anios', '/api/v1/pagos/conceptos', '/api/v1/alumnos?page=0&size=1', '/api/v1/matriculas/campanias',
             '/api/v1/notificaciones/mias/no-leidas/conteo', '/api/v1/asistencia/justificaciones', '/api/v1/tareas?page=0&size=1',
             '/api/v1/examenes?page=0&size=1', '/api/v1/calificaciones/promedios?seccionId=1&periodoId=1']
    limite = time.time() + segundos
    while time.time() < limite:
        estados = [call('GET', r, 'admin')[0] for r in rutas]
        if all(s in (200, 404, 409) for s in estados):
            return True
        time.sleep(5)
    print('El backend no respondió a tiempo:', estados)
    return False


def multipart(campo, nombre, contenido, tipo='application/pdf'):
    b = '----eduspace' + uuid.uuid4().hex
    cuerpo = (f'--{b}\r\nContent-Disposition: form-data; name="{campo}"; filename="{nombre}"\r\nContent-Type: {tipo}\r\n\r\n').encode() + contenido + f'\r\n--{b}--\r\n'.encode()
    return cuerpo, f'multipart/form-data; boundary={b}'


class Resultado:
    def __init__(self):
        self.fallos = []

    def ok(self, nombre, res, esperado=(200, 201, 204)):
        s, d = res
        bien = s in (esperado if isinstance(esperado, tuple) else (esperado,))
        print(('OK  ' if bien else 'ERR ') + f'{nombre:62} {s}' + ('' if bien else '  ' + json.dumps(d, ensure_ascii=False)[:220]))
        if not bien:
            self.fallos.append(nombre)
        return d

    def fin(self):
        print('\nFALLOS:', self.fallos if self.fallos else 'ninguno')
        return 1 if self.fallos else 0
