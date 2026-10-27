import json, urllib.request, urllib.error, subprocess, sys, os, time, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import base64
TOK = {}
SEMILLAS = {(1,'ADMIN'): ('admin','admin123'), (2,'SECRETARIA'): ('secretaria','secre123'), (20,'CAJERO'): ('cajero','cajero123')}
CLAVE = 'Clave12345'
def _login(username, clave):
    req = urllib.request.Request('http://localhost:8090/api/v1/auth/login', method='POST',
        data=json.dumps({'username': username, 'password': clave}).encode(), headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return json.loads(r.read())['access_token']
    except urllib.error.HTTPError as e:
        if e.code == 401: return None
        raise
def tok(etiqueta, rol):
    """Token real de auth-ms. Las semillas (admin, secretaria, cajero) existen; los demás usuarios se crean al primer uso."""
    k = (etiqueta, rol)
    if k in TOK: return TOK[k]
    user, clave = SEMILLAS.get(k, (f'{rol.lower()}{etiqueta}', CLAVE))
    t = _login(user, clave)
    if t is None and k not in SEMILLAS:
        admin = tok(1, 'ADMIN')
        req = urllib.request.Request('http://localhost:8090/api/v1/auth/usuarios', method='POST',
            data=json.dumps({'username': user, 'email': f'{user}@colegio.pe', 'nombres': user.capitalize(), 'apellidos': 'Prueba',
                             'roles': [rol], 'clave': clave}).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + admin})
        urllib.request.urlopen(req, timeout=30).read()
        t = _login(user, clave)
    if t is None: raise RuntimeError(f'No se pudo iniciar sesión como {user}')
    TOK[k] = t
    return t
def uid(who):
    """usuarioId real (claim del token) del usuario de prueba."""
    p = tok(*who).split('.')[1]
    return json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4)))['usuarioId']
ADM, SEC, DOC, CAJ, APO, ALU = (1,'ADMIN'), (2,'SECRETARIA'), (5,'DOCENTE'), (20,'CAJERO'), (700,'APODERADO'), (301,'ALUMNO')
BASE = {'auth':8090,'plan':8091,'alum':8092,'noti':8099,'mat':8093,'asis':8094,'tar':8098,'pago':8096,'exa':8097,'cal':8095,'gw':18080}
def call(svc, method, path, who=ADM, body=None, expect=None, raw=False):
    url = f'http://localhost:{BASE[svc]}{path}'
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if who: req.add_header('Authorization', 'Bearer ' + tok(*who))
    if data is not None: req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=30) as r: code, txt = r.status, r.read().decode()
    except urllib.error.HTTPError as e: code, txt = e.code, e.read().decode()
    try: js = json.loads(txt) if txt else None
    except Exception: js = txt
    ok = (expect is None and code < 300) or (expect is not None and code == expect)
    print(f"  {'OK ' if ok else 'ERR'} {method} :{BASE[svc]}{path} -> {code}" + ('' if ok else f"  {str(js)[:300]}"))
    if not ok: FAILS.append((method, path, code, str(js)[:300]))
    return js
FAILS = []
def topic(t, n=50):
    out = subprocess.run(['podman','exec','e2e-kafka','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:9092',
        '--topic',t,'--from-beginning','--timeout-ms','4000'], capture_output=True, text=True).stdout.strip().splitlines()
    return [json.loads(l) for l in out if l.startswith('{')]
def esperar(fn, msg, seg=40):
    for _ in range(seg):
        try:
            v = fn()
            if v: return v
        except Exception: pass
        time.sleep(1)
    print('  TIMEOUT esperando:', msg); FAILS.append(('wait', msg, 0, '')); return None
def check(cond, msg):
    print(f"  {'OK ' if cond else 'ERR'} {msg}")
    if not cond: FAILS.append(('check', msg, 0, ''))
