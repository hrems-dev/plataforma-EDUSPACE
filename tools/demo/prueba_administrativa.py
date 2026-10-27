"""Verifica contra el sistema real las operaciones de las pantallas administrativas (usa los datos de semilla.py)."""
import json, uuid
from lib import *
R = Resultado()
anio = call('GET', '/api/v1/planificacion/anios/activo', 'admin')[1]
sec = next(s for s in call('GET', f"/api/v1/planificacion/secciones?anioId={anio['id']}", 'admin')[1] if s['nombre'] == 'A')
per = next(p for p in call('GET', f"/api/v1/planificacion/periodos?anioId={anio['id']}", 'admin')[1] if p['estado'] == 'ACTIVO')
suf = str(int(time.time()))[-5:]

# --- importación CSV
csv = ('numeroDocumento,nombres,apellidoPaterno,apellidoMaterno,fechaNacimiento,sexo,apoderadoNumeroDocumento,apoderadoNombres,apoderadoApellidos,apoderadoParentesco\n'
       f'8{suf}01,Ana,Gómez,Ruiz,2016-05-01,F,3{suf}01,Rosa,Ruiz,MADRE\n'
       f'8{suf}02,Luis,Pérez,,2016-13-45,M,3{suf}02,Juan,Pérez,PADRE\n').encode('utf-8')
cuerpo, ct = multipart('archivo', 'alumnos.csv', csv, 'text/csv')
r = R.ok('importar CSV (1 válida, 1 con fecha inválida)', call('POST', '/api/v1/alumnos/importacion', 'secretaria', crudo=cuerpo, ctype=ct))
print('   total', r['total'], '| creados', r['creados'], '| fallidos', r['fallidos'], '|', [(f['linea'], f['creado'], f.get('error')) for f in r['filas']])
cuerpo, ct = multipart('archivo', 'malo.csv', b'a,b\n1,2\n', 'text/csv')
R.ok('importar CSV sin columnas obligatorias (debe rechazarse)', call('POST', '/api/v1/alumnos/importacion', 'secretaria', crudo=cuerpo, ctype=ct), (400, 409, 422))

# --- asistencia: reporte y bandeja
rep = R.ok('reporte de asistencia de la sección', call('GET', f"/api/v1/asistencia/secciones/{sec['id']}/reporte", 'secretaria'))
print('   alumnos:', rep['totalAlumnos'], '| fila:', {k: rep['alumnos'][0]['dias'][k] for k in ('sesiones', 'presentes', 'faltas', 'tardanzas', 'justificadas', 'porcentajeAsistencia')}, '| exceso:', rep['alumnos'][0]['excesoFaltas'])
R.ok('reporte con rango de fechas', call('GET', f"/api/v1/asistencia/secciones/{sec['id']}/reporte?desde={anio['fechaInicio']}&hasta={HOY if False else '2099-01-01'}", 'secretaria'))
for est in ('PENDIENTE', 'APROBADA', 'RECHAZADA'):
    b = R.ok(f'bandeja de justificaciones {est}', call('GET', f'/api/v1/asistencia/justificaciones?estado={est}&page=0&size=5', 'secretaria'))
    print('   ', est, '->', b['totalElementos'])
R.ok('resumen de asistencia de un alumno', call('GET', '/api/v1/asistencia/alumnos/1/resumen', 'secretaria'))

# --- calificaciones: cierre, boletines, reapertura (con el bimestre 3: un criterio manual y notas completas)
per3 = next(p for p in call('GET', f"/api/v1/planificacion/periodos?anioId={anio['id']}", 'admin')[1] if p['numero'] == 3)
mate = next(a for a in call('GET', f"/api/v1/planificacion/asignaciones?seccionId={sec['id']}", 'docente')[1] if a['cursoNombre'] == 'Matemática')
base = {'anioId': anio['id'], 'periodoId': per3['id'], 'seccionId': sec['id'], 'cursoId': mate['cursoId']}
q = f"periodoId={per3['id']}&seccionId={sec['id']}&cursoId={mate['cursoId']}"
crits = call('GET', f'/api/v1/calificaciones/criterios?{q}', 'docente')[1]
if not crits:
    R.ok('criterio único (100 %)', call('POST', '/api/v1/calificaciones/criterios', 'docente', {**base, 'nombre': 'Nota del bimestre', 'peso': 100, 'origenTipo': 'MANUAL'}))
    crits = call('GET', f'/api/v1/calificaciones/criterios?{q}', 'docente')[1]
reg = call('GET', f"/api/v1/calificaciones/registro?seccionId={sec['id']}&cursoId={mate['cursoId']}&periodoId={per3['id']}", 'docente')[1]
R.ok('notas completas del bimestre', call('PUT', '/api/v1/calificaciones/notas/lote', 'docente', {'seccionId': sec['id'], 'cursoId': mate['cursoId'], 'periodoId': per3['id'], 'criterioId': crits[0]['id'], 'notas': [{'matriculaId': a['matriculaId'], 'alumnoId': a['alumnoId'], 'valor': 14 + i} for i, a in enumerate(reg['alumnos'])]}))
R.ok('promedios de la sección', call('GET', f"/api/v1/calificaciones/promedios?seccionId={sec['id']}&periodoId={per3['id']}", 'secretaria'))
R.ok('cerrar periodo con una nota faltante (debe explicar cuál)', call('POST', '/api/v1/calificaciones/periodos/cerrar', 'secretaria', {'seccionId': sec['id'], 'periodoId': per['id']}), 400)
R.ok('cerrar periodo (SECRETARIA)', call('POST', '/api/v1/calificaciones/periodos/cerrar', 'secretaria', {'seccionId': sec['id'], 'periodoId': per3['id']}))
prom = call('GET', f"/api/v1/calificaciones/promedios?seccionId={sec['id']}&periodoId={per3['id']}", 'secretaria')[1]
print('   promedios tras cerrar:', sorted({p['estado'] for p in prom}), [p['promedio'] for p in prom])
bol = R.ok('generar boletines de la sección', call('POST', '/api/v1/calificaciones/boletines/generar', 'secretaria', {'seccionId': sec['id'], 'periodoId': per3['id']}))
print('   boletines generados:', len(bol))
if bol:
    R.ok('URL de descarga del boletín', call('GET', f"/api/v1/calificaciones/boletines/{bol[0]['id']}/url", 'secretaria'))
    R.ok('boletines del alumno', call('GET', f"/api/v1/calificaciones/boletines?alumnoId={bol[0]['alumnoId']}", 'secretaria'))
    lib = R.ok('libreta del alumno', call('GET', f"/api/v1/calificaciones/alumnos/{bol[0]['alumnoId']}/libreta", 'secretaria'))
    print('   libreta:', [(c['cursoNombre'], c['notasPorPeriodo']) for c in lib['cursos']][:1], '| boletines:', len(lib['boletines']))
R.ok('resultados anuales de la sección', call('GET', f"/api/v1/calificaciones/resultados-anuales?seccionId={sec['id']}", 'secretaria'))
R.ok('SECRETARIA no puede reabrir (403)', call('POST', '/api/v1/calificaciones/periodos/reabrir', 'secretaria', {'seccionId': sec['id'], 'periodoId': per3['id'], 'motivo': 'x'}), 403)
R.ok('ADMIN reabre el periodo', call('POST', '/api/v1/calificaciones/periodos/reabrir', 'admin', {'seccionId': sec['id'], 'periodoId': per3['id'], 'motivo': 'Corrección de una nota'}))

# --- cobranza
ec = R.ok('estado de cuenta (SECRETARIA)', call('GET', '/api/v1/pagos/alumnos/1/estado-cuenta', 'secretaria'))
print('   cuotas:', len(ec['cuotas']), '| pendiente S/', ec['totalPendiente'], '| vencido S/', ec['totalVencido'])
R.ok('cuotas paginadas con filtros', call('GET', '/api/v1/pagos/cuotas?estado=VENCIDA&page=0&size=5', 'secretaria'))
desc = call('GET', '/api/v1/pagos/descuentos', 'admin')[1][0]
R.ok('ADMIN asigna descuento a un alumno', call('POST', '/api/v1/pagos/descuentos/asignar', 'admin', {'alumnoId': 2, 'descuentoId': desc['id'], 'anioId': anio['id'], 'motivo': 'Hermanos'}), (200, 201, 409))
R.ok('SECRETARIA no asigna descuentos (403)', call('POST', '/api/v1/pagos/descuentos/asignar', 'secretaria', {'alumnoId': 2, 'descuentoId': desc['id'], 'anioId': anio['id']}), 403)
rc = R.ok('reporte de cobranza', call('GET', '/api/v1/pagos/reportes/cobranza?desde=2026-01-01T00:00:00&hasta=2099-01-01T23:59:59&page=0&size=5', 'secretaria'))
print('   pagos:', rc['totalElementos'])
pen = next(c for c in ec['cuotas'] if c['estado'] in ('PENDIENTE', 'VENCIDA'))
p = R.ok('cajero cobra una cuota para anularla', call('POST', '/api/v1/pagos', 'cajero', {'alumnoId': 1, 'montoTotal': pen['saldo'], 'medio': 'EFECTIVO', 'idempotencyKey': str(uuid.uuid4()), 'tipoComprobante': 'RECIBO', 'detalles': [{'cuotaId': pen['id'], 'montoAplicado': pen['saldo']}]}))
R.ok('detalle del pago', call('GET', f"/api/v1/pagos/{p['id']}", 'secretaria'))
R.ok('comprobante del pago', call('GET', f"/api/v1/pagos/{p['id']}/comprobante", 'secretaria'))
R.ok('SECRETARIA no anula pagos (403)', call('PATCH', f"/api/v1/pagos/{p['id']}/anular", 'secretaria', {'motivo': 'x'}), 403)
R.ok('ADMIN anula el pago', call('PATCH', f"/api/v1/pagos/{p['id']}/anular", 'admin', {'motivo': 'Cobro duplicado de prueba'}))
rep = call('GET', f"/api/v1/pagos/caja/1/reporte", 'cajero')
R.ok('reporte de la caja', rep)

# --- notificaciones
R.ok('plantillas (ADMIN)', call('GET', '/api/v1/notificaciones/plantillas', 'admin'))
R.ok('envío manual (ADMIN)', call('POST', '/api/v1/notificaciones/enviar', 'admin', {'usuarioIds': [5, 6], 'titulo': 'Reunión de padres', 'mensaje': 'El jueves a las 6 p. m.', 'canales': ['IN_APP']}))
R.ok('preferencias: guardar', call('PUT', '/api/v1/notificaciones/preferencias', 'apoderado', [{'tipo': 'PAGO_POR_VENCER', 'canal': 'EMAIL', 'habilitada': False}]))
R.ok('preferencias: leer', call('GET', '/api/v1/notificaciones/preferencias', 'apoderado'))
n = R.ok('notificaciones del apoderado', call('GET', '/api/v1/notificaciones/mias?page=0&size=5', 'apoderado'))
print('   total:', n['total'], '| tipos:', sorted({x['tipo'] for x in n['contenido']}))
if n['contenido']:
    R.ok('marcar una como leída', call('PATCH', f"/api/v1/notificaciones/{n['contenido'][0]['id']}/leida", 'apoderado', {}))
R.ok('contador de no leídas', call('GET', '/api/v1/notificaciones/mias/no-leidas/conteo', 'apoderado'))
R.ok('leer todas', call('PATCH', '/api/v1/notificaciones/mias/leer-todas', 'apoderado', {}))
raise SystemExit(R.fin())
