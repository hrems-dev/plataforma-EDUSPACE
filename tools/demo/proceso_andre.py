"""Proceso transaccional de ANDRE (matricula-ms): matrícula con cupo atómico, pago y compensación (saga coreografiada).

Pasos y qué demuestra (ver docs/presentacion/01-procesos-transaccionales.md):
  1. INICIAR   : pre-matrícula + cupo reservado en UNA transacción (UPDATE ... WHERE ocupados < capacidad) + evento matricula.creada (outbox).
  2. PAGO FALLIDO: tarjeta rechazada -> no se cobra, la matrícula sigue PRE_MATRICULA, el cupo no se libera.
  3. PAGO OK   : pago-ms cobra (idempotente) -> matrícula MATRICULADO -> pago-ms genera las 10 pensiones por evento.
  4. COMPENSAR : retiro -> evento matricula.retirada -> pago-ms anula cuotas pendientes y el cupo vuelve.
Usa la matrícula en línea del sitio público; no necesita sesión salvo para consultar cupos y retirar (admin).

Uso:  AUTH_MODE=eduspace python3 tools/demo/proceso_andre.py
"""
import os, sys, time, json, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault('AUTH_MODE', 'eduspace')
from lib import call, GW, Resultado

R = Resultado()
PUB = GW + '/api/v1/publico/matricula'

def pub(metodo, ruta, cuerpo=None, token=None):
    cab = {'Content-Type': 'application/json'}
    if token:
        cab['X-Matricula-Token'] = token
    r = urllib.request.Request(PUB + ruta, data=json.dumps(cuerpo).encode() if cuerpo is not None else None, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(r) as x:
            return x.status, json.loads(x.read() or 'null')
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or 'null')

def cupo(seccion_nombre, grado_id, anio_id):
    c = call('GET', f'/api/v1/matriculas/cupos?anioId={anio_id}&gradoId={grado_id}', 'admin')[1]
    f = next((x for x in c if x['seccionNombre'] == seccion_nombre), None)
    return f['ocupados'] if f else None

oferta = pub('GET', '/oferta')[1]
grado = next(g for n in oferta['niveles'] for g in n['grados'] if g['vacantes'] > 0 and g['matricula'] and 'Primaria' in n['nombre'])
anio = oferta['anioId']
sufijo = str(int(time.time()))[-6:]
doc_alumno, doc_apod = '95' + sufijo, '45' + sufijo
solicitud = {'gradoId': grado['id'], 'acepto': True, 'sitio': '',
             'alumno': {'tipoDocumento': 'DNI', 'numeroDocumento': doc_alumno, 'nombres': 'Alumno', 'apellidoPaterno': 'Demostración',
                        'apellidoMaterno': 'Saga', 'fechaNacimiento': '2018-01-15', 'sexo': 'M'},
             'apoderado': {'tipoDocumento': 'DNI', 'numeroDocumento': doc_apod, 'nombres': 'Apoderado', 'apellidos': 'Demostración Saga',
                           'telefono': '999000111', 'email': f'familia.{sufijo}@ejemplo.pe', 'parentesco': 'PADRE'}}

print(f'\n-- 1. INICIAR en {grado["nombre"]} (vacantes antes: {grado["vacantes"]})')
s, ini = pub('POST', '', solicitud)
R.ok('POST /publico/matricula (pre-matrícula + cupo + evento en una transacción)', (s, ini), 201)
if s != 201:
    sys.exit(R.fin())
ref, tok = ini['referencia'], ini['token']
print(f'   {ref} reservada en la sección {ini["seccion"]}; cupos ocupados ahora: {cupo(ini["seccion"], grado["id"], anio)}')
ocupados_reserva = cupo(ini['seccion'], grado['id'], anio)

print('\n-- 2. PAGO FALLIDO (tarjeta de rechazo): no se cobra y la reserva se mantiene')
s, d = pub('POST', f'/{ref}/pagar', {'tarjeta': {'numero': '4000 0000 0000 0002', 'titular': 'DEMO', 'vencimiento': '12/30', 'cvv': '123'}}, tok)
print(f'   HTTP {s}: {d.get("mensaje")}')
if s != 402:
    R.fallos.append('el rechazo no devolvió 402')
print(f'   cupos ocupados siguen en {cupo(ini["seccion"], grado["id"], anio)} (reserva intacta)')

print('\n-- 3. PAGO OK: cobro idempotente -> POR_APROBAR (aún NO matriculado, sin cuentas ni pensiones)')
s, res = pub('POST', f'/{ref}/pagar', {'tarjeta': {'numero': '4242 4242 4242 4242', 'titular': 'DEMO', 'vencimiento': '12/30', 'cvv': '123'}}, tok)
R.ok('POST …/pagar con 4242…', (s, res), 200)
print(f'   estado del trámite={res.get("estado")} comprobante={res.get("comprobante")} pensiones={len(res.get("pensiones", []))} usuario alumno={res.get("usuarioAlumno")}')
if res.get('estado') != 'PAGADA' or res.get('usuarioAlumno'):
    R.fallos.append('tras el pago la matrícula debe quedar en revisión, sin usuario todavía')
s2, res2 = pub('POST', f'/{ref}/pagar', {'tarjeta': {'numero': '4242 4242 4242 4242', 'titular': 'DEMO', 'vencimiento': '12/30', 'cvv': '123'}}, tok)
print(f'   repetir el pago -> HTTP {s2}, mismo comprobante: {res2.get("comprobante") == res.get("comprobante")} (no cobra dos veces)')
m = call('GET', f'/api/v1/matriculas?texto={ref}', 'admin')[1]['contenido'][0]
print(f'   estado de la matrícula: {m["estado"]}')
if m['estado'] != 'POR_APROBAR':
    R.fallos.append('la matrícula pagada debe quedar POR_APROBAR')

print('\n-- 3b. APROBACIÓN ADMINISTRATIVA: solo ADMIN; genera pensiones y envía el acceso por correo')
s, _ = call('PATCH', f'/api/v1/matriculas/{m["id"]}/aprobar', 'secretaria')
print(f'   secretaría intenta aprobar -> HTTP {s} (debe ser 403)')
if s != 403:
    R.fallos.append('secretaría no debe poder aprobar')
R.ok('PATCH /matriculas/{id}/aprobar (admin)', call('PATCH', f'/api/v1/matriculas/{m["id"]}/aprobar', 'admin'))
time.sleep(6)
s, res = pub('GET', f'/{ref}', None, tok)
print(f'   trámite={res.get("estado")} usuario alumno={res.get("usuarioAlumno")} apoderado={res.get("usuarioApoderado")} pensiones={len(res.get("pensiones", []))} correo={res.get("correo")}')
if res.get('estado') != 'APROBADA' or not res.get('usuarioAlumno') or not res.get('pensiones'):
    R.fallos.append('tras aprobar debe haber cuentas y pensiones')
try:
    msgs = json.load(urllib.request.urlopen('http://localhost:18025/api/v1/messages'))['messages']
    mios = [x['Subject'] for x in msgs if any(f'familia.{sufijo}@' in d['Address'] for d in x['To'])]
    print('   correos recibidos por la familia (Mailpit):', mios)
    if not any('aprobada' in x.lower() for x in mios):
        R.fallos.append('no llegó el correo de aprobación')
except OSError:
    print('   (Mailpit no responde en :18025; se omite la verificación del correo)')

print('\n-- 4. COMPENSAR: retirar la matrícula devuelve el cupo y anula lo pendiente')
R.ok('PATCH /matriculas/{id}/retiro', call('PATCH', f'/api/v1/matriculas/{m["id"]}/retiro', 'admin', {'motivo': 'Demostración del proceso transaccional'}))
time.sleep(4)
print(f'   cupos ocupados vuelven a {cupo(ini["seccion"], grado["id"], anio)} (antes del retiro: {ocupados_reserva})')
sys.exit(R.fin())
