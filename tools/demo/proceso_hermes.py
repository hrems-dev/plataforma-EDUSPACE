"""Proceso transaccional de HERMES (planificacion-ms): armar y publicar un horario «todo o nada».

Qué demuestra (ver docs/presentacion/01-procesos-transaccionales.md):
  1. SIMULAR: el generador corre el mismo código que al guardar y termina con setRollbackOnly -> la base no cambia.
  2. INVARIANTES: un choque (misma sección, día y bloque) se rechaza con 409; nada queda a medias.
  3. PUBLICAR: versión nueva + evento horario.publicado en la MISMA transacción (outbox) -> avisos a docentes y familias.

Uso (con el sistema levantado en modo cuentas del colegio):  AUTH_MODE=eduspace python3 tools/demo/proceso_hermes.py
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault('AUTH_MODE', 'eduspace')
from lib import call, Resultado

R = Resultado()
anio = R.ok('año activo', call('GET', '/api/v1/planificacion/anios/activo', 'admin'))
secciones = call('GET', f'/api/v1/planificacion/secciones?anioId={anio["id"]}', 'admin')[1]
primaria = [s['id'] for s in secciones if 'Primaria' in s['gradoNombre']]

def total_clases():
    return len(call('GET', f'/api/v1/planificacion/horarios/anio/{anio["id"]}', 'admin')[1])

antes = total_clases()
print(f'\n-- 1. SIMULAR: clases guardadas antes = {antes}')
sim = R.ok('generar con simular=true', call('POST', '/api/v1/planificacion/horarios/generar', 'admin',
           {'anioId': anio['id'], 'seccionIds': primaria, 'reemplazar': True, 'asignarDocentes': False, 'simular': True}))
despues = total_clases()
print(f'   colocaría {sim["horasColocadas"]} clases ({sim["seccionesGeneradas"]} secciones) y la base sigue en {despues}'
      f' -> {"SIN CAMBIOS (rollback)" if despues == antes else "¡CAMBIÓ!"}')
if despues != antes:
    R.fallos.append('la simulación modificó la base')

print('\n-- 2. INVARIANTE: no se puede duplicar una clase en el mismo día y bloque')
sec = primaria[0]
clases = call('GET', f'/api/v1/planificacion/horarios/seccion/{sec}', 'admin')[1]['clases']
if clases:
    c = clases[0]
    s, d = call('POST', '/api/v1/planificacion/horarios', 'admin', {'asignacionId': c['asignacionId'], 'bloqueId': c['bloqueId'], 'diaSemana': c['diaSemana']})
    print(f'   intento duplicado -> HTTP {s}: {d.get("mensaje") if isinstance(d, dict) else d}')
    if s not in (409, 422):
        R.fallos.append('el choque no fue rechazado')
    print(f'   clases de la sección siguen en {len(call("GET", f"/api/v1/planificacion/horarios/seccion/{sec}", "admin")[1]["clases"])} (nada a medias)')
else:
    print('   (la sección no tiene clases: ejecuta primero el armado automático)')

print('\n-- 3. PUBLICAR: versión + evento en la misma transacción')
val = R.ok('validar la sección', call('GET', f'/api/v1/planificacion/horarios/validar/{sec}', 'admin'))
print('   válido:', val.get('valido'), '| choques:', val.get('choques'))
if val.get('valido'):
    pub = R.ok('publicar (crea la versión y escribe horario.publicado en el outbox)', call('POST', f'/api/v1/planificacion/horarios/publicar/seccion/{sec}', 'admin'), (200, 201))
    print(f'   versión publicada: {pub.get("version")} -> notificaciones-ms consumirá horario.publicado y avisará a docentes y familias')
    hist = call('GET', f'/api/v1/planificacion/horarios/publicar/seccion/{sec}', 'admin')[1]
    print('   historial de versiones:', [h['version'] for h in hist])
sys.exit(R.fin())
