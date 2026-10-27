"""Datos de demostración de EduSpace, por etapas, a través del Gateway.

  python3 semilla.py base        colegio: año, periodos, cursos, malla, sección, docente, horario, alumnos, matrículas y cobros
  python3 semilla.py academico   asistencia, criterios y notas, tareas y exámenes (como DOCENTE)
  python3 semilla.py familia     entregas, intento de examen, justificación y pago en línea (como ALUMNO y APODERADO)
  python3 semilla.py todo

Necesita el backend arriba y los usuarios admin, secretaria, cajero, docente, apoderado y alumno (ver tools/demo/lib.py).
Con Keycloak, el atributo usuarioId de docente/apoderado/alumno es 4, 5 y 6; la semilla los vincula a sus fichas.
"""
import datetime
import sys
import uuid

from lib import call, esperar_backend, multipart, time, Resultado

R = Resultado()
HOY = datetime.date.today()
ANIO = str(HOY.year)
USUARIO_DOCENTE, USUARIO_APODERADO, USUARIO_ALUMNO = 4, 5, 6


def dia_habil(d):
    while d.weekday() >= 5:
        d -= datetime.timedelta(days=1)
    return d


def buscar_o_crear(lista, clave, valor, crear):
    for x in lista:
        if x.get(clave) == valor:
            return x
    return crear()


def contexto():
    anio = call('GET', '/api/v1/planificacion/anios/activo', 'admin')[1]
    sec = next(s for s in call('GET', f"/api/v1/planificacion/secciones?anioId={anio['id']}", 'admin')[1] if s['nombre'] == 'A' and '4°' in s['gradoNombre'])
    return anio, sec


# ------------------------------------------------------------------ base
def base():
    anios = call('GET', '/api/v1/planificacion/anios', 'admin')[1]
    anio = buscar_o_crear(anios, 'nombre', ANIO, lambda: R.ok('crear año', call('POST', '/api/v1/planificacion/anios', 'admin', {'nombre': ANIO, 'fechaInicio': f'{ANIO}-03-02', 'fechaFin': f'{ANIO}-12-18'})))
    if anio['estado'] == 'PLANIFICADO':
        R.ok('activar año', call('PATCH', f"/api/v1/planificacion/anios/{anio['id']}/activar", 'admin', {}))
    periodos = call('GET', f"/api/v1/planificacion/periodos?anioId={anio['id']}", 'admin')[1]
    if not periodos:
        rangos = [(1, '03-02', '05-08'), (2, '05-11', '07-17'), (3, '08-03', '10-09'), (4, '10-12', '12-18')]
        for n, ini, fin in rangos:
            p = R.ok(f'periodo {n}', call('POST', '/api/v1/planificacion/periodos', 'admin', {'anioId': anio['id'], 'numero': n, 'nombre': f'Bimestre {n}', 'fechaInicio': f'{ANIO}-{ini}', 'fechaFin': f'{ANIO}-{fin}'}))
            if f'{ANIO}-{ini}' <= HOY.isoformat() <= f'{ANIO}-{fin}':
                R.ok(f'activar periodo {n}', call('PATCH', f"/api/v1/planificacion/periodos/{p['id']}/estado", 'admin', {'estado': 'ACTIVO'}))
    grado = next(g for g in call('GET', '/api/v1/planificacion/grados', 'admin')[1] if g['numero'] == 4 and g['nivelNombre'] == 'Primaria')
    cursos = {c['nombre']: c for c in call('GET', '/api/v1/planificacion/cursos', 'admin')[1]}
    horas = {'Matemática': 5, 'Comunicación': 5, 'Ciencia y Tecnología': 3}
    for i, nombre in enumerate(horas):
        if nombre not in cursos:
            cursos[nombre] = R.ok(f'curso {nombre}', call('POST', '/api/v1/planificacion/cursos', 'admin', {'codigo': ['MAT', 'COM', 'CYT'][i], 'nombre': nombre, 'area': nombre, 'activo': True}))
    malla = call('GET', f"/api/v1/planificacion/malla/grado/{grado['id']}", 'admin')[1]
    for nombre, h in horas.items():
        if not any(c['cursoId'] == cursos[nombre]['id'] for c in malla['cursos']):
            R.ok(f'malla {nombre} {h} h', call('POST', '/api/v1/planificacion/malla', 'admin', {'gradoId': grado['id'], 'cursoId': cursos[nombre]['id'], 'horasSemanales': h}))
    aula = buscar_o_crear(call('GET', '/api/v1/planificacion/aulas', 'admin')[1], 'codigo', 'A-4A', lambda: R.ok('aula', call('POST', '/api/v1/planificacion/aulas', 'admin', {'codigo': 'A-4A', 'nombre': 'Aula 4° A', 'tipo': 'AULA', 'capacidad': 30, 'activo': True})))
    secs = call('GET', f"/api/v1/planificacion/secciones?anioId={anio['id']}", 'admin')[1]
    sec = next((s for s in secs if s['nombre'] == 'A' and s['gradoId'] == grado['id']), None) or R.ok('sección 4° A', call('POST', '/api/v1/planificacion/secciones', 'admin', {'anioId': anio['id'], 'gradoId': grado['id'], 'nombre': 'A', 'turno': 'MANANA', 'capacidad': 30, 'aulaId': aula['id'], 'activo': True}))
    # docente
    docs = call('GET', '/api/v1/planificacion/docentes?q=Paredes&page=0&size=5', 'admin')[1]['contenido']
    doc = docs[0] if docs else R.ok('docente', call('POST', '/api/v1/planificacion/docentes', 'secretaria', {'tipoDocumento': 'DNI', 'numeroDocumento': '40111222', 'nombres': 'Jorge', 'apellidos': 'Paredes', 'email': 'docente@eduspace.com', 'especialidad': 'Matemática'}))
    if not doc.get('usuarioId'):
        R.ok('vincular docente con su usuario', call('PATCH', f"/api/v1/planificacion/docentes/{doc['id']}/usuario", 'admin', {'usuarioId': USUARIO_DOCENTE}))
    asig = {a['cursoNombre']: a for a in call('GET', f"/api/v1/planificacion/asignaciones?seccionId={sec['id']}", 'secretaria')[1]}
    for nombre in horas:
        if nombre not in asig:
            asig[nombre] = R.ok(f'asignar {nombre}', call('POST', '/api/v1/planificacion/asignaciones', 'secretaria', {'seccionId': sec['id'], 'cursoId': cursos[nombre]['id'], 'docenteId': doc['id']}))
    # bloques y horario
    bl = call('GET', '/api/v1/planificacion/bloques?turno=MANANA', 'admin')[1]
    if not bl:
        for o, i, f, rec in [(1, '08:00', '08:45', False), (2, '08:45', '09:30', False), (3, '09:30', '10:15', False), (4, '10:15', '10:35', True), (5, '10:35', '11:20', False), (6, '11:20', '12:05', False)]:
            R.ok(f'bloque {o}', call('POST', '/api/v1/planificacion/bloques', 'admin', {'turno': 'MANANA', 'orden': o, 'horaInicio': i, 'horaFin': f, 'esReceso': rec}))
        bl = call('GET', '/api/v1/planificacion/bloques?turno=MANANA', 'admin')[1]
    clase = [b for b in sorted(bl, key=lambda b: b['orden']) if not b['esReceso']]
    hs = call('GET', f"/api/v1/planificacion/horarios/seccion/{sec['id']}", 'secretaria')[1]
    if not hs['clases']:
        plan = {'Matemática': [(1, 0), (2, 1), (3, 0), (4, 1), (5, 0)], 'Comunicación': [(1, 1), (2, 0), (3, 1), (4, 0), (5, 1)], 'Ciencia y Tecnología': [(1, 2), (2, 2), (3, 2)]}
        for nombre, celdas in plan.items():
            for dia, idx in celdas:
                R.ok(f'horario {nombre} d{dia} b{idx + 1}', call('POST', '/api/v1/planificacion/horarios', 'secretaria', {'asignacionId': asig[nombre]['id'], 'bloqueId': clase[idx]['id'], 'diaSemana': dia}))
        R.ok('publicar horario', call('POST', f"/api/v1/planificacion/horarios/publicar/seccion/{sec['id']}", 'secretaria'))
    if not call('GET', f"/api/v1/planificacion/feriados?anioId={anio['id']}", 'admin')[1]:
        R.ok('feriado', call('POST', '/api/v1/planificacion/feriados', 'admin', {'anioId': anio['id'], 'fecha': f'{ANIO}-12-08', 'descripcion': 'Inmaculada Concepción'}))
    # cobranza
    conceptos = {c['tipo']: c for c in call('GET', '/api/v1/pagos/conceptos', 'admin')[1]}
    nivel = sec['nivelId']
    tarifas = call('GET', f"/api/v1/pagos/tarifas?anioId={anio['id']}", 'admin')[1]
    for tipo, monto, cuotas, mes in [('MATRICULA', 100, 1, 3), ('PENSION', 300, 10, 3)]:
        if not any(t['conceptoId'] == conceptos[tipo]['id'] for t in tarifas):
            R.ok(f'tarifa {tipo}', call('POST', '/api/v1/pagos/tarifas', 'admin', {'anioId': anio['id'], 'nivelId': nivel, 'gradoId': None, 'conceptoId': conceptos[tipo]['id'], 'monto': monto, 'numCuotas': cuotas, 'mesInicio': mes, 'diaVencimiento': 5, 'activo': True}))
    if not call('GET', '/api/v1/pagos/descuentos', 'admin')[1]:
        R.ok('descuento hermanos 10 %', call('POST', '/api/v1/pagos/descuentos', 'admin', {'nombre': 'Hermanos', 'tipo': 'PORCENTAJE', 'valor': 10, 'aplicaA': 'PENSION', 'activo': True}))
    if not call('GET', f"/api/v1/matriculas/campanias?anioId={anio['id']}", 'admin')[1]:
        c = R.ok('campaña', call('POST', '/api/v1/matriculas/campanias', 'admin', {'anioId': anio['id'], 'nombre': f'Matrícula {ANIO}', 'fechaInicio': f'{ANIO}-01-05', 'fechaFin': f'{ANIO}-12-18'}))
        R.ok('abrir campaña', call('PATCH', f"/api/v1/matriculas/campanias/{c['id']}/abrir", 'admin', {}))
    alumnos(anio, sec)


def subir(aid, tipo):
    cuerpo, ct = multipart('archivo', f'{tipo}.pdf', b'%PDF-1.4 demo')
    return call('POST', f'/api/v1/alumnos/{aid}/documentos?tipo={tipo}', 'secretaria', crudo=cuerpo, ctype=ct)


def asegurar_expediente(aid):
    """DNI, partida y ficha SIAGIE subidos y verificados (sin eso la matrícula se rechaza)."""
    docs = call('GET', f'/api/v1/alumnos/{aid}/documentos', 'secretaria')[1]
    for tipo in ('DNI', 'PARTIDA_NACIMIENTO', 'FICHA_SIAGIE'):
        d = next((x for x in docs if x['tipo'] == tipo), None)
        if d is None:
            s, d = subir(aid, tipo)
            if s != 201:
                time.sleep(2)  # el almacenamiento de archivos puede tardar en estar listo al primer uso
                s, d = subir(aid, tipo)
        if isinstance(d, dict) and d.get('id') and not d.get('verificado'):
            call('PATCH', f"/api/v1/alumnos/{aid}/documentos/{d['id']}/verificado", 'secretaria', {'verificado': True})


def alumnos(anio, sec):
    datos = [
        ('Lucía', 'Mendoza', 'Ríos', '2016-04-12', 'F', ('Carmen', 'Ríos', '40123456', 'carmen@ejemplo.com'), 'MADRE'),
        ('Mateo', 'Mendoza', 'Ríos', '2017-02-03', 'M', None, 'MADRE'),
        ('Sofía', 'Vargas', 'Luna', '2016-08-21', 'F', ('Jorge', 'Vargas', '40777888', 'jvargas@ejemplo.com'), 'PADRE'),
        ('Diego', 'Salas', 'Vega', '2016-11-30', 'M', ('Rosa', 'Vega', '40555666', 'rvega@ejemplo.com'), 'MADRE'),
    ]
    existentes = {a['nombreCompleto']: a for a in call('GET', '/api/v1/alumnos?page=0&size=50', 'secretaria')[1]['contenido']}
    ids = {}
    apoderado_carmen = None
    if 'Mendoza Ríos, Lucía' in existentes:  # repetición de la semilla: recuperar a la apoderada ya creada
        apoderado_carmen = call('GET', f"/api/v1/alumnos/{existentes['Mendoza Ríos, Lucía']['id']}", 'secretaria')[1]['apoderados'][0]['apoderado']['id']
    for i, (nom, ap, am, nac, sexo, apo, par) in enumerate(datos):
        nombre = f'{ap} {am}, {nom}'
        if nombre in existentes:
            ids[nom] = existentes[nombre]['id']
            continue
        if apo:
            vinculo = {'apoderado': {'numeroDocumento': apo[2], 'nombres': apo[0], 'apellidos': apo[1], 'telefono': '99911122' + str(i), 'email': apo[3]}, 'parentesco': par, 'esPrincipal': True, 'esResponsablePago': True, 'viveConAlumno': True, 'puedeRecoger': True}
        else:  # Mateo: mismo apoderado que Lucía (hermanos)
            vinculo = {'apoderadoId': apoderado_carmen, 'parentesco': par, 'esPrincipal': True, 'esResponsablePago': True, 'viveConAlumno': True, 'puedeRecoger': True}
        a = R.ok(f'alumno {nom}', call('POST', '/api/v1/alumnos', 'secretaria', {'numeroDocumento': f'7{i}2345{i}1', 'nombres': nom, 'apellidoPaterno': ap, 'apellidoMaterno': am, 'fechaNacimiento': nac, 'sexo': sexo, 'distrito': 'Miraflores', 'apoderados': [vinculo]}))
        ids[nom] = a['id']
        if nom == 'Lucía':
            apoderado_carmen = a['apoderados'][0]['apoderado']['id']
            R.ok('vincular apoderada Carmen con su usuario', call('PATCH', f'/api/v1/apoderados/{apoderado_carmen}/usuario', 'admin', {'usuarioId': USUARIO_APODERADO}))
            R.ok('vincular alumna Lucía con su usuario', call('PATCH', f"/api/v1/alumnos/{a['id']}/usuario", 'admin', {'usuarioId': USUARIO_ALUMNO}))
    for aid in ids.values():
        asegurar_expediente(aid)
    matriculas = call('GET', f"/api/v1/matriculas?anioId={anio['id']}&page=0&size=50", 'secretaria')[1]['contenido']
    for nom, aid in ids.items():
        if any(m['alumnoId'] == aid and m['estado'] in ('PRE_MATRICULA', 'MATRICULADO') for m in matriculas):
            continue
        R.ok(f'matrícula {nom}', call('POST', '/api/v1/matriculas', 'secretaria', {'alumnoId': aid, 'seccionId': sec['id'], 'tipo': 'NUEVA'}))
    time.sleep(3)
    cobrar(['Lucía', 'Mateo', 'Sofía'], ids)  # Diego queda en pre-matrícula


def cobrar(nombres, ids):
    cj = call('GET', '/api/v1/pagos/caja/actual', 'cajero')
    if cj[0] != 200:
        R.ok('abrir caja', call('POST', '/api/v1/pagos/caja/abrir', 'cajero', {'montoApertura': 100}))
    for nom in nombres:
        aid = ids[nom]
        cuota = None
        for _ in range(20):
            ec = call('GET', f'/api/v1/pagos/alumnos/{aid}/estado-cuenta', 'cajero')[1]
            cuota = next((c for c in ec.get('cuotas', []) if c['conceptoTipo'] == 'MATRICULA' and c['estado'] in ('PENDIENTE', 'VENCIDA', 'PARCIAL')), None) if isinstance(ec, dict) else None
            if cuota or any(c['conceptoTipo'] == 'MATRICULA' and c['estado'] == 'PAGADA' for c in (ec.get('cuotas', []) if isinstance(ec, dict) else [])):
                break
            time.sleep(2)
        if not cuota:
            print(f'   (sin cuota de matrícula pendiente para {nom}; ya estaba pagada o no se generó)')
            continue
        R.ok(f'cobrar matrícula {nom}', call('POST', '/api/v1/pagos', 'cajero', {'alumnoId': aid, 'pagadorNombre': 'Apoderado', 'montoTotal': cuota['saldo'], 'medio': 'EFECTIVO', 'idempotencyKey': str(uuid.uuid4()), 'tipoComprobante': 'RECIBO', 'detalles': [{'cuotaId': cuota['id'], 'montoAplicado': cuota['saldo']}]}))
    time.sleep(4)


# ------------------------------------------------------------------ académico (como DOCENTE)
def ahora_iso(dias=0, horas=0):
    return (datetime.datetime.now() + datetime.timedelta(days=dias, hours=horas)).strftime('%Y-%m-%dT%H:%M:%S')


def academico():
    anio, sec = contexto()
    periodo = next(p for p in call('GET', f"/api/v1/planificacion/periodos?anioId={anio['id']}", 'docente')[1] if p['estado'] == 'ACTIVO')
    mi = R.ok('docente: /docentes/mi', call('GET', '/api/v1/planificacion/docentes/mi', 'docente'))
    asigs = {a['cursoNombre']: a for a in call('GET', f"/api/v1/planificacion/asignaciones?docenteId={mi['docenteId']}", 'docente')[1]}
    mate = asigs['Matemática']
    # asistencia: ayer (cerrada) y hoy (abierta)
    for etiqueta, dia, cerrar in [('ayer', dia_habil(HOY - datetime.timedelta(days=1)), True), ('hoy', dia_habil(HOY), False)]:
        s = R.ok(f'asistencia {etiqueta}: abrir hoja {dia}', call('POST', '/api/v1/asistencia/sesiones', 'docente', {'seccionId': sec['id'], 'fecha': dia.isoformat(), 'tipo': 'DIA'}))
        if s.get('estado') == 'CERRADA':
            continue
        estados = {'Mendoza Ríos, Lucía': 'PRESENTE', 'Mendoza Ríos, Mateo': 'TARDANZA', 'Vargas Luna, Sofía': 'FALTA'}
        items = [{'matriculaId': a['matriculaId'], 'estado': estados.get(a['alumnoNombre'], 'PRESENTE'), **({'minutosTarde': 10} if estados.get(a['alumnoNombre']) == 'TARDANZA' else {})} for a in s['alumnos']]
        R.ok(f'asistencia {etiqueta}: guardar registro ({len(items)} alumnos)', call('PUT', f"/api/v1/asistencia/sesiones/{s['id']}/registro", 'docente', {'items': items}))
        if cerrar:
            R.ok(f'asistencia {etiqueta}: cerrar hoja', call('PATCH', f"/api/v1/asistencia/sesiones/{s['id']}/cerrar", 'docente', {}))
    # criterios y notas de Matemática
    crits = call('GET', f"/api/v1/calificaciones/criterios?periodoId={periodo['id']}&seccionId={sec['id']}&cursoId={mate['cursoId']}", 'docente')[1]
    base = {'anioId': anio['id'], 'periodoId': periodo['id'], 'seccionId': sec['id'], 'cursoId': mate['cursoId']}
    if not crits:
        for nombre, peso, origen in [('Tareas', 30, 'TAREAS'), ('Examen bimestral', 40, 'EXAMENES'), ('Participación', 30, 'MANUAL')]:
            R.ok(f'criterio {nombre} {peso} %', call('POST', '/api/v1/calificaciones/criterios', 'docente', {**base, 'nombre': nombre, 'peso': peso, 'origenTipo': origen}))
        crits = call('GET', f"/api/v1/calificaciones/criterios?periodoId={periodo['id']}&seccionId={sec['id']}&cursoId={mate['cursoId']}", 'docente')[1]
    v = R.ok('validar pesos', call('GET', f"/api/v1/calificaciones/criterios/validar?periodoId={periodo['id']}&seccionId={sec['id']}&cursoId={mate['cursoId']}", 'docente'))
    print('   suma de pesos:', v['sumaPesos'], '| válido:', v['esValido'])
    reg = R.ok('registro de notas', call('GET', f"/api/v1/calificaciones/registro?seccionId={sec['id']}&cursoId={mate['cursoId']}&periodoId={periodo['id']}", 'docente'))
    manual = next(c for c in crits if c['origenTipo'] == 'MANUAL')
    valores = {'Mendoza Ríos, Lucía': 18, 'Mendoza Ríos, Mateo': 15, 'Vargas Luna, Sofía': 12}
    notas = [{'matriculaId': a['matriculaId'], 'alumnoId': a['alumnoId'], 'valor': valores[a['alumnoNombre']]} for a in reg['alumnos'] if a['alumnoNombre'] in valores]
    R.ok('guardar notas de Participación (lote)', call('PUT', '/api/v1/calificaciones/notas/lote', 'docente', {'seccionId': sec['id'], 'cursoId': mate['cursoId'], 'periodoId': periodo['id'], 'criterioId': manual['id'], 'notas': notas}))
    # tarea publicada
    tareas = call('GET', '/api/v1/tareas?page=0&size=20', 'docente')[1]['contenido']
    if not any(t['titulo'] == 'Ejercicios de fracciones' for t in tareas):
        t = R.ok('crear tarea', call('POST', '/api/v1/tareas', 'docente', {'periodoId': periodo['id'], 'seccionId': sec['id'], 'cursoId': mate['cursoId'], 'titulo': 'Ejercicios de fracciones', 'descripcion': 'Resolver la guía 3, páginas 12 a 14.', 'fechaEntrega': ahora_iso(3), 'permiteTardia': True, 'puntajeMaximo': 20}))
        R.ok('publicar tarea', call('PATCH', f"/api/v1/tareas/{t['id']}/publicar", 'docente', {}))
    # banco de preguntas y examen
    bancos = call('GET', f"/api/v1/examenes/preguntas?cursoId={mate['cursoId']}&page=0&size=50", 'docente')[1]['contenido']
    if not bancos:
        preguntas = [
            {'cursoId': mate['cursoId'], 'tema': 'Fracciones', 'tipo': 'OPCION_UNICA', 'enunciado': '¿Cuánto es 1/2 + 1/4?', 'puntaje': 5, 'opciones': [{'texto': '3/4', 'esCorrecta': True, 'orden': 1}, {'texto': '2/6', 'esCorrecta': False, 'orden': 2}, {'texto': '1/6', 'esCorrecta': False, 'orden': 3}]},
            {'cursoId': mate['cursoId'], 'tema': 'Fracciones', 'tipo': 'VERDADERO_FALSO', 'enunciado': '2/4 es una fracción equivalente a 1/2.', 'puntaje': 5, 'opciones': [{'texto': 'Verdadero', 'esCorrecta': True, 'orden': 1}, {'texto': 'Falso', 'esCorrecta': False, 'orden': 2}]},
            {'cursoId': mate['cursoId'], 'tema': 'Geometría', 'tipo': 'OPCION_MULTIPLE', 'enunciado': 'Marca los números pares.', 'puntaje': 5, 'opciones': [{'texto': '2', 'esCorrecta': True, 'orden': 1}, {'texto': '3', 'esCorrecta': False, 'orden': 2}, {'texto': '8', 'esCorrecta': True, 'orden': 3}]},
            {'cursoId': mate['cursoId'], 'tema': 'Problemas', 'tipo': 'ABIERTA', 'enunciado': 'Explica con tus palabras qué es una fracción.', 'puntaje': 5},
        ]
        for p in preguntas:
            R.ok(f"pregunta {p['tipo']}", call('POST', '/api/v1/examenes/preguntas', 'docente', p))
        bancos = call('GET', f"/api/v1/examenes/preguntas?cursoId={mate['cursoId']}&page=0&size=50", 'docente')[1]['contenido']
    examenes = call('GET', '/api/v1/examenes?page=0&size=20', 'docente')[1]['contenido']
    if not any(e['titulo'] == 'Examen de fracciones' for e in examenes):
        items = [{'preguntaId': p['id'], 'orden': i + 1, 'puntaje': p['puntaje']} for i, p in enumerate(bancos)]
        e = R.ok('crear examen', call('POST', '/api/v1/examenes', 'docente', {'anioId': anio['id'], 'periodoId': periodo['id'], 'seccionId': sec['id'], 'cursoId': mate['cursoId'], 'titulo': 'Examen de fracciones', 'instrucciones': 'Lee con calma. Tienes 40 minutos.', 'fechaInicio': ahora_iso(0, -1), 'fechaFin': ahora_iso(0, 6), 'duracionMin': 40, 'intentosMax': 1, 'barajarPreguntas': True, 'preguntas': items}))
        R.ok('publicar examen', call('PATCH', f"/api/v1/examenes/{e['id']}/publicar", 'docente', {}))
    R.ok('pendientes de asignar', call('GET', f"/api/v1/calificaciones/pendientes-asignar?seccionId={sec['id']}&cursoId={mate['cursoId']}", 'docente'))


# ------------------------------------------------------------------ familia (como SECRETARIA, APODERADO y ALUMNO)
def familia():
    anio, sec = contexto()
    ayer = dia_habil(HOY - datetime.timedelta(days=1))
    hijos = {a['nombreCompleto']: a['id'] for a in call('GET', '/api/v1/alumnos?page=0&size=50', 'secretaria')[1]['contenido']}
    mateo, lucia = hijos['Mendoza Ríos, Mateo'], hijos['Mendoza Ríos, Lucía']
    # secretaría corrige la asistencia de ayer (hoja cerrada): Mateo faltó
    ses = call('GET', f"/api/v1/asistencia/sesiones?seccionId={sec['id']}&fecha={ayer}", 'secretaria')[1]
    fila = next(a for a in ses[0]['alumnos'] if a['alumnoId'] == mateo)
    if fila['estado'] not in ('FALTA', 'JUSTIFICADO'):
        R.ok('secretaría corrige: Mateo FALTA ayer', call('PATCH', f"/api/v1/asistencia/detalle/{fila['detalleId']}", 'secretaria', {'estado': 'FALTA', 'motivo': 'El apoderado avisó que no asistió'}))
    # apoderado: ve la asistencia de sus hijos y justifica
    R.ok('apoderado: asistencia de mis hijos', call('GET', '/api/v1/asistencia/mias', 'apoderado'))
    if not call('GET', '/api/v1/asistencia/justificaciones/mias', 'apoderado')[1]:
        j = R.ok('apoderado: solicita justificación', call('POST', '/api/v1/asistencia/justificaciones', 'apoderado', {'alumnoId': mateo, 'fechaDesde': ayer.isoformat(), 'fechaHasta': ayer.isoformat(), 'tipo': 'MEDICA', 'motivo': 'Cita médica programada'}))
        u = R.ok('apoderado: URL firmada para el sustento', call('POST', f"/api/v1/asistencia/justificaciones/{j['id']}/archivo-url", 'apoderado', {'nombreArchivo': 'constancia.pdf', 'contentType': 'application/pdf'}))
        print('   URL de subida:', (u.get('uploadUrl') or '')[:60] + '…', '| ruta:', u.get('ruta'))
    pend = call('GET', '/api/v1/asistencia/justificaciones?estado=PENDIENTE', 'secretaria')[1]['contenido']
    if pend:
        R.ok('secretaría aprueba la justificación', call('PATCH', f"/api/v1/asistencia/justificaciones/{pend[0]['id']}/resolver", 'secretaria', {'resolucion': 'APROBADA', 'comentario': 'Constancia recibida'}))
    # alumno: examen
    disp = R.ok('alumno: exámenes disponibles', call('GET', '/api/v1/examenes/disponibles', 'alumno'))
    ex = next((e for e in disp if e['puedeRendir']), None)
    if ex:
        it = R.ok('alumno: iniciar intento', call('POST', f"/api/v1/examenes/{ex['examenId']}/intentos", 'alumno', {}))
        for p in it['preguntas']:
            if p['tipo'] == 'ABIERTA':
                cuerpo = {'textoRespuesta': 'Una fracción es una parte de un entero.'}
            elif p['tipo'] == 'OPCION_MULTIPLE':
                cuerpo = {'opcionesIds': [o['id'] for o in p['opciones'] if o['texto'] in ('2', '8')]}
            else:
                cuerpo = {'opcionesIds': [p['opciones'][0]['id']]}
            R.ok(f"alumno: responder pregunta {p['orden']} ({p['tipo']})", call('PUT', f"/api/v1/examenes/intentos/{it['intentoId']}/respuestas/{p['preguntaId']}", 'alumno', cuerpo))
        res = R.ok('alumno: enviar examen', call('POST', f"/api/v1/examenes/intentos/{it['intentoId']}/enviar", 'alumno', {}))
        print('   nota:', res.get('nota'), '| puntaje', res.get('puntajeObtenido'), '/', res.get('puntajeTotal'), '| requiere revisión:', res.get('requiereRevisionAbiertas'))
    R.ok('alumno: mis exámenes', call('GET', '/api/v1/examenes/mias', 'alumno'))
    # alumno: tarea con archivo
    pend_t = R.ok('alumno: tareas pendientes', call('GET', '/api/v1/tareas/mias/pendientes', 'alumno'))
    if pend_t:
        tid = pend_t[0]['tareaId']
        u = R.ok('alumno: URL de subida del archivo', call('POST', f'/api/v1/tareas/{tid}/entregas/archivo-url', 'alumno', {'nombreArchivo': 'tarea-lucia.pdf', 'contentType': 'application/pdf', 'tamanoBytes': 20}))
        import urllib.request
        try:
            r = urllib.request.Request(u['uploadUrl'], data=b'%PDF-1.4 tarea de Lucia', method='PUT', headers={'Content-Type': 'application/pdf'})
            print('   subida directa a MinIO:', urllib.request.urlopen(r).status)
        except Exception as e:  # noqa: BLE001
            print('   subida directa a MinIO falló:', e)
        R.ok('alumno: entregar tarea', call('POST', f'/api/v1/tareas/{tid}/entregas', 'alumno', {'comentario': 'Aquí está mi tarea', 'archivos': [{'nombreArchivo': 'tarea-lucia.pdf', 'ruta': u['ruta'], 'contentType': 'application/pdf', 'tamanoBytes': 20}]}))
        ent = call('GET', f'/api/v1/tareas/{tid}/entregas', 'docente')[1]
        e = next((x['entrega'] for x in ent if x.get('entrega')), None)
        if e:
            R.ok('docente: califica la entrega', call('PATCH', f"/api/v1/tareas/entregas/{e['id']}/calificar", 'docente', {'nota': 17, 'retroalimentacion': 'Muy bien'}))
    R.ok('alumno: mis tareas', call('GET', '/api/v1/tareas/mias', 'alumno'))
    # familia: asistencia, notas, matrículas, horario, pagos
    R.ok('alumno: mi asistencia', call('GET', '/api/v1/asistencia/mias', 'alumno'))
    R.ok('apoderado: libretas de mis hijos', call('GET', '/api/v1/calificaciones/mias', 'apoderado'))
    R.ok('alumno: mis notas', call('GET', '/api/v1/calificaciones/mias', 'alumno'))
    R.ok('apoderado: matrículas de mis hijos', call('GET', '/api/v1/matriculas/mias', 'apoderado'))
    R.ok('alumno: mi horario (sección)', call('GET', f"/api/v1/planificacion/horarios/seccion/{sec['id']}", 'alumno'))
    R.ok('apoderado: mis hijos', call('GET', '/api/v1/alumnos/mios', 'apoderado'))
    cuotas = R.ok('apoderado: cuotas de mis hijos', call('GET', '/api/v1/pagos/mias/cuotas', 'apoderado'))
    pens = next((c for c in cuotas if c['conceptoTipo'] == 'PENSION' and c['estado'] in ('PENDIENTE', 'VENCIDA')), None)
    if pens:
        R.ok('apoderado: pago en línea de una pensión', call('POST', '/api/v1/pagos/online', 'apoderado', {'alumnoId': pens['alumnoId'], 'montoTotal': pens['saldo'], 'medio': 'ONLINE_SIMULADO', 'idempotencyKey': str(uuid.uuid4()), 'detalles': [{'cuotaId': pens['id'], 'montoAplicado': pens['saldo']}]}))
    R.ok('apoderado: estado de cuenta de Lucía', call('GET', f'/api/v1/pagos/alumnos/{lucia}/estado-cuenta', 'apoderado'))
    R.ok('notificaciones del apoderado', call('GET', '/api/v1/notificaciones/mias?page=0&size=5', 'apoderado'))


if __name__ == '__main__':
    etapa = sys.argv[1] if len(sys.argv) > 1 else 'base'
    if not esperar_backend():
        raise SystemExit(2)
    if etapa in ('base', 'todo'):
        base()
    if etapa in ('academico', 'todo'):
        academico()
    if etapa in ('familia', 'todo'):
        familia()
    raise SystemExit(R.fin())
