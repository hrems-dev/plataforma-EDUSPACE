#!/usr/bin/env python3
"""Deja el sistema listo para la exposición (se puede repetir sin problema).

Requiere el sistema levantado con login propio (tools/e2e/levantar.sh --auth-propio).
  1. Cuentas de la demo con clave conocida y SIN cambio obligatorio (docente, alumno, apoderado).
  2. Un examen publicado y disponible para la sección de la alumna.
  3. Una tarea publicada del mismo curso, para entregar en vivo.
  4. Imprime la «hoja de credenciales» y lo que verifica el estado.
"""
import datetime, json, os, sys, urllib.request, urllib.error

GW = os.environ.get('GATEWAY', 'http://localhost:18080')
CLAVE = 'Expo12345'
ADMIN = ('admin', 'admin123')
# docente de Matemática y Comunicación de 4.º A; su alumna y la apoderada de la alumna
CUENTAS = {'docente': 'jorge.paredes', 'alumno': 'lucia.mendoza', 'apoderado': 'carmen.rios'}
CURSO_ID, SECCION_ID = 1, 1


def http(metodo, ruta, token=None, cuerpo=None):
    h = {'Content-Type': 'application/json'}
    if token:
        h['Authorization'] = 'Bearer ' + token
    r = urllib.request.Request(GW + ruta, data=json.dumps(cuerpo).encode() if cuerpo is not None else None, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(r, timeout=30) as x:
            t = x.read().decode()
            return x.status, (json.loads(t) if t.strip()[:1] in ('{', '[') else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try:
            return e.code, json.loads(t)
        except ValueError:
            return e.code, t
    except OSError as e:
        sys.exit(f'No llega al Gateway ({GW}): {e}. ¿Está levantado el sistema?')


def login(u, c):
    s, d = http('POST', '/api/v1/auth/login', cuerpo={'username': u, 'password': c})
    return d['access_token'] if s == 200 else None


def paso(msg, ok=True):
    print(('  ✔ ' if ok else '  ✘ ') + msg)
    if not ok:
        sys.exit(1)


adm = login(*ADMIN) or sys.exit('No se pudo entrar como admin (admin/admin123). ¿Está el sistema en modo --auth-propio?')
print('== Cuentas')
s, usuarios = http('GET', '/api/v1/auth/usuarios?size=500', adm)
por_nombre = {u['username']: u for u in (usuarios if isinstance(usuarios, list) else usuarios.get('contenido', []))}
tokens = {}
for rol, nombre in CUENTAS.items():
    if nombre not in por_nombre:
        paso(f'No existe la cuenta {nombre}', False)
    if login(nombre, CLAVE):
        tokens[rol] = login(nombre, CLAVE)
        paso(f'{rol}: {nombre} ya tiene la clave de la demo')
        continue
    s, _ = http('POST', f"/api/v1/auth/usuarios/{por_nombre[nombre]['id']}/restablecer-por-documento", adm, {'documento': 'Inicial12345'})
    t = login(nombre, 'Inicial12345')
    s, _ = http('POST', '/api/v1/auth/cambiar-clave', t, {'claveActual': 'Inicial12345', 'claveNueva': CLAVE})
    tokens[rol] = login(nombre, CLAVE)
    paso(f'{rol}: {nombre} lista', tokens[rol] is not None)

doc, alu = tokens['docente'], tokens['alumno']
print('== Examen y tarea de la demostración')
s, anio = http('GET', '/api/v1/planificacion/anios/activo', doc)
s, periodos = http('GET', f"/api/v1/planificacion/periodos?anioId={anio['id']}", doc)
hoy = datetime.date.today().isoformat()
periodo = next((p for p in periodos if p['fechaInicio'] <= hoy <= p['fechaFin']), periodos[0])
s, disp = http('GET', '/api/v1/examenes/disponibles', alu)
ya = [e for e in (disp if isinstance(disp, list) else []) if e['titulo'] == 'Examen de demostración' and e.get('puedeRendir') and e.get('intentosRealizados', 0) == 0]
if ya:
    paso('hay un examen de la demostración sin intentos usados')
else:
    def pregunta(tipo, enun, ops):
        s, d = http('POST', '/api/v1/examenes/preguntas', doc, {'cursoId': CURSO_ID, 'tema': 'Demostración', 'tipo': tipo, 'enunciado': enun, 'puntaje': 5, 'opciones': ops})
        return d['id']
    p1 = pregunta('OPCION_UNICA', '¿Cuánto es 1/2 + 1/2?', [{'texto': '1', 'esCorrecta': True, 'orden': 1}, {'texto': '2', 'esCorrecta': False, 'orden': 2}, {'texto': '1/4', 'esCorrecta': False, 'orden': 3}])
    p2 = pregunta('VERDADERO_FALSO', '3/4 es mayor que 1/2', [{'texto': 'Verdadero', 'esCorrecta': True, 'orden': 1}, {'texto': 'Falso', 'esCorrecta': False, 'orden': 2}])
    p3 = pregunta('OPCION_UNICA', '¿Cuánto es 3 × 4?', [{'texto': '12', 'esCorrecta': True, 'orden': 1}, {'texto': '7', 'esCorrecta': False, 'orden': 2}])
    ahora = datetime.datetime.now()
    f = lambda d: d.strftime('%Y-%m-%dT%H:%M:%S')
    s, ex = http('POST', '/api/v1/examenes', doc, {
        'anioId': anio['id'], 'periodoId': periodo['id'], 'seccionId': SECCION_ID, 'cursoId': CURSO_ID, 'titulo': 'Examen de demostración',
        'duracionMin': 10, 'intentosMax': 3, 'fechaInicio': f(ahora - datetime.timedelta(minutes=5)), 'fechaFin': f(ahora + datetime.timedelta(days=3)),
        'preguntas': [{'preguntaId': p, 'orden': i + 1, 'puntaje': 5} for i, p in enumerate((p1, p2, p3))]})
    paso('examen creado', s == 201)
    s, _ = http('PATCH', f"/api/v1/examenes/{ex['id']}/publicar", doc)
    paso('examen publicado (3 intentos, 10 min)', s in (200, 204))

s, mias = http('GET', '/api/v1/tareas/mias', alu)
if any(t['titulo'] == 'Tarea de demostración' and t['estadoEntrega'] in ('SIN_ENTREGAR', 'DEVUELTA') for t in mias):
    paso('la tarea de la demostración ya está pendiente')
else:
    entrega = (datetime.datetime.now() + datetime.timedelta(days=5)).strftime('%Y-%m-%dT%H:%M:%S')
    s, t = http('POST', '/api/v1/tareas', doc, {'periodoId': periodo['id'], 'seccionId': SECCION_ID, 'cursoId': CURSO_ID, 'titulo': 'Tarea de demostración',
                                               'descripcion': 'Resuelve la página 25 y súbela en PDF o foto.', 'fechaEntrega': entrega, 'permiteTardia': True, 'puntajeMaximo': 20})
    paso('tarea creada', s == 201)
    s, _ = http('PATCH', f"/api/v1/tareas/{t['id']}/publicar", doc)
    paso('tarea publicada (llega aviso a la familia)', s in (200, 204))

s, mats = http('GET', f'/api/v1/tareas/materiales?seccionId={SECCION_ID}&cursoId={CURSO_ID}', doc)
if any(m['titulo'] == 'Material de demostración' for m in (mats if isinstance(mats, list) else [])):
    paso('el material de la demostración ya está publicado')
else:
    s, _ = http('POST', '/api/v1/tareas/materiales', doc, {'seccionId': SECCION_ID, 'cursoId': CURSO_ID, 'titulo': 'Material de demostración',
                                                          'descripcion': 'Videos y lecturas de la unidad', 'tipo': 'ENLACE', 'enlace': 'https://es.khanacademy.org/math/arithmetic'})
    paso('material (enlace) publicado: la familia recibe el aviso', s == 201)

print('== Notas del bimestre (la familia solo ve periodos cerrados)')
s, regs = http('GET', f"/api/v1/planificacion/asignaciones?seccionId={SECCION_ID}", adm)
cursos = sorted({a['cursoId'] for a in regs})
def registro(c):
    return http('GET', f"/api/v1/calificaciones/registro?seccionId={SECCION_ID}&cursoId={c}&periodoId={periodo['id']}", adm)[1]
if registro(cursos[0]).get('estadoPeriodo') == 'CERRADO':
    paso('el bimestre de 4.° A ya está cerrado: la familia ve sus notas')
else:
    for c in cursos:
        r = registro(c)
        for crit in r['criterios']:
            faltan = [a for a in r['alumnos'] if str(crit['id']) not in {str(k) for k in a['notasPorCriterioId']}]
            if faltan:
                http('PUT', '/api/v1/calificaciones/notas/lote', adm, {'seccionId': SECCION_ID, 'cursoId': c, 'periodoId': periodo['id'], 'criterioId': crit['id'],
                     'notas': [{'matriculaId': a['matriculaId'], 'alumnoId': a['alumnoId'], 'valor': 14 + (a['alumnoId'] + c + crit['id']) % 6} for a in faltan]})
    s, d = http('POST', '/api/v1/calificaciones/periodos/cerrar', adm, {'seccionId': SECCION_ID, 'periodoId': periodo['id']})
    paso('bimestre de 4.° A cerrado: notas publicadas para alumnos y apoderados' if s == 200 else f"no se pudo cerrar el bimestre: {d.get('message', d) if isinstance(d, dict) else d}", s == 200)

print('\n== HOJA DE CREDENCIALES (clave de todas las cuentas de la demo:', CLAVE + ')')
print(f"  Docente    {CUENTAS['docente']:<18} Matemática y Comunicación de 4.° A")
print(f"  Alumna     {CUENTAS['alumno']:<18} 4.° A (examen y tarea disponibles)")
print(f"  Apoderada  {CUENTAS['apoderado']:<18} madre de la alumna")
print('  Administración: admin / admin123 · Secretaría: secretaria / secre123 · Caja: cajero / cajero123')
print('  Correos de la matrícula en línea: http://localhost:18025 (Mailpit)')
