from lib import *
R = Resultado()
anio = call('GET', '/api/v1/planificacion/anios/activo', 'admin')[1]
suf = str(int(time.time()))[-5:]
grados = call('GET', '/api/v1/planificacion/grados', 'admin')[1]
g = next(x for x in grados if 'Primaria' in x['nivelNombre'] and x['numero'] == 4)
print('grado:', g['nivelNombre'], g['nombre'], '(id', g['id'], ')')
# cursos y malla (en planificación solo el ADMIN escribe; la secretaria edita horarios, asignaciones y docentes)
R.ok('SECRETARIA no crea cursos (403)', call('POST', '/api/v1/planificacion/cursos', 'secretaria', {'codigo': 'X', 'nombre': 'x', 'activo': True}), 403)
ids = {}
for cod, nom, horas in [('MAT'+suf,'Matemática',5), ('COM'+suf,'Comunicación',5), ('CYT'+suf,'Ciencia y Tecnología',3)]:
    c = R.ok(f'crear curso {nom}', call('POST', '/api/v1/planificacion/cursos', 'admin', {'codigo': cod, 'nombre': nom, 'activo': True}))
    ids[nom] = c['id']
    R.ok(f'malla: agregar {nom} ({horas} h)', call('POST', '/api/v1/planificacion/malla', 'admin', {'gradoId': g['id'], 'cursoId': c['id'], 'horasSemanales': horas}))
m = R.ok('malla del grado', call('GET', f"/api/v1/planificacion/malla/grado/{g['id']}", 'secretaria'))
print('   total horas:', m['totalHoras'], '| cursos:', [(c['cursoNombre'], c['horasSemanales']) for c in m['cursos']])
R.ok('malla: cambiar horas', call('PUT', f"/api/v1/planificacion/malla/{m['cursos'][0]['id']}", 'admin', {'horasSemanales': 4}))
# sección, docente y asignación
sec = R.ok('crear sección 4° A', call('POST', '/api/v1/planificacion/secciones', 'admin', {'anioId': anio['id'], 'gradoId': g['id'], 'nombre': 'A'+suf[-2:], 'turno': 'MANANA', 'capacidad': 30, 'activo': True}))
doc = R.ok('crear docente', call('POST', '/api/v1/planificacion/docentes', 'secretaria', {'tipoDocumento': 'DNI', 'numeroDocumento': '8'+suf+'01', 'nombres': 'Jorge', 'apellidos': 'Paredes', 'especialidad': 'Matemática'}))
R.ok('docente: vincular usuario de Keycloak (id 4)', call('PATCH', f"/api/v1/planificacion/docentes/{doc['id']}/usuario", 'admin', {'usuarioId': 4}))
asig = {}
for nom in ids:
    asig[nom] = R.ok(f'asignar {nom}', call('POST', '/api/v1/planificacion/asignaciones', 'secretaria', {'seccionId': sec['id'], 'cursoId': ids[nom], 'docenteId': doc['id']}))
R.ok('asignación duplicada (debe rechazarse)', call('POST', '/api/v1/planificacion/asignaciones', 'secretaria', {'seccionId': sec['id'], 'cursoId': ids['Matemática'], 'docenteId': doc['id']}), (409, 400))
R.ok('listar asignaciones', call('GET', f"/api/v1/planificacion/asignaciones?seccionId={sec['id']}", 'secretaria'))
# bloques
bl = call('GET', '/api/v1/planificacion/bloques?turno=MANANA', 'secretaria')[1]
if len(bl) < 3:
    for orden, ini, fin, rec in [(1,'08:00','08:45',False),(2,'08:45','09:30',False),(3,'09:30','10:15',False),(4,'10:15','10:35',True),(5,'10:35','11:20',False),(6,'11:20','12:05',False)]:
        R.ok(f'bloque {orden} {ini}-{fin}', call('POST', '/api/v1/planificacion/bloques', 'admin', {'turno': 'MANANA', 'orden': orden, 'horaInicio': ini, 'horaFin': fin, 'esReceso': rec}))
    bl = call('GET', '/api/v1/planificacion/bloques?turno=MANANA', 'secretaria')[1]
bl = sorted(bl, key=lambda b: b['orden']); clases = [b for b in bl if not b['esReceso']]
print('   bloques de clase:', [(b['orden'], b['horaInicio'][:5]) for b in clases])
# horario
h1 = R.ok('horario: Matemática lunes bloque 1', call('POST', '/api/v1/planificacion/horarios', 'secretaria', {'asignacionId': asig['Matemática']['id'], 'bloqueId': clases[0]['id'], 'diaSemana': 1}))
R.ok('horario: choque en la misma celda (debe rechazarse)', call('POST', '/api/v1/planificacion/horarios', 'secretaria', {'asignacionId': asig['Comunicación']['id'], 'bloqueId': clases[0]['id'], 'diaSemana': 1}), (409, 400))
R.ok('horario: Comunicación lunes bloque 2', call('POST', '/api/v1/planificacion/horarios', 'secretaria', {'asignacionId': asig['Comunicación']['id'], 'bloqueId': clases[1]['id'], 'diaSemana': 1}))
R.ok('horario: mover clase a martes (PUT)', call('PUT', f"/api/v1/planificacion/horarios/{h1['id']}", 'secretaria', {'asignacionId': asig['Matemática']['id'], 'bloqueId': clases[0]['id'], 'diaSemana': 2}))
hs = R.ok('horario de la sección', call('GET', f"/api/v1/planificacion/horarios/seccion/{sec['id']}", 'secretaria'))
print('   bloques:', len(hs['bloques']), '| clases:', len(hs['clases']), '| turno:', hs['turno'])
v = R.ok('validar horario', call('GET', f"/api/v1/planificacion/horarios/validar/{sec['id']}", 'secretaria'))
print('   válido:', v['valido'], '| incompletos:', [(c['cursoNombre'], f"{c['horasProgramadas']}/{c['horasRequeridas']}") for c in v['cursosIncompletos']], '| choques:', v['choques'])
R.ok('publicar horario incompleto', call('POST', f"/api/v1/planificacion/horarios/publicar/seccion/{sec['id']}", 'secretaria'), (200, 201, 409, 400, 422))
R.ok('horario del docente', call('GET', f"/api/v1/planificacion/horarios/docente/{doc['id']}", 'secretaria'))
R.ok('docente: /docentes/mi con su propio token (Keycloak id 4)', call('GET', '/api/v1/planificacion/docentes/mi', 'docente'))
# feriados
f = R.ok('crear feriado', call('POST', '/api/v1/planificacion/feriados', 'admin', {'anioId': anio['id'], 'fecha': '2027-07-28', 'descripcion': 'Fiestas Patrias'}))
R.ok('es hábil el 28/07?', call('GET', '/api/v1/planificacion/feriados/es-habil?fecha=2027-07-28', 'secretaria'))
R.ok('editar feriado', call('PUT', f"/api/v1/planificacion/feriados/{f['id']}", 'admin', {'anioId': anio['id'], 'fecha': '2027-07-28', 'descripcion': 'Fiestas Patrias (día 1)'}))
R.ok('eliminar feriado', call('DELETE', f"/api/v1/planificacion/feriados/{f['id']}", 'admin'))
R.ok('quitar clase del horario', call('DELETE', f"/api/v1/planificacion/horarios/{h1['id']}", 'secretaria'))
raise SystemExit(R.fin())
