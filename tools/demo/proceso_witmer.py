"""Proceso transaccional de WITMER (pago-ms): cobro idempotente con comprobante y evento, y su compensación (anulación).

Qué demuestra (ver docs/presentacion/01-procesos-transaccionales.md):
  1. COBRAR     : cuota + pago + detalle + comprobante (correlativo bloqueado FOR UPDATE) + evento pago.confirmado: TODO o NADA.
  2. IDEMPOTENCIA: reenviar el mismo idempotencyKey (reintento de red) devuelve el MISMO pago; la cuota baja una sola vez.
  3. VALIDACIÓN : cobrar más que el saldo se rechaza y no deja rastro.
  4. COMPENSAR  : anular el pago revierte el saldo de la cuota y anula el comprobante (y avisa por evento).
Requiere un cajero (caja abierta). Deja la caja abierta: ciérrala desde la pantalla Caja.

Uso:  AUTH_MODE=eduspace python3 tools/demo/proceso_witmer.py
"""
import os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault('AUTH_MODE', 'eduspace')
from lib import call, Resultado

R = Resultado()
s, d = call('POST', '/api/v1/pagos/caja/abrir', 'cajero', {'montoApertura': 0})
print('caja:', 'abierta ahora' if s in (200, 201) else f'ya estaba abierta ({s})')

cuotas = call('GET', '/api/v1/pagos/cuotas?estado=PENDIENTE&size=20', 'admin')[1]
lista = cuotas['contenido'] if isinstance(cuotas, dict) else cuotas
cuota = next((c for c in lista if float(c['saldo']) >= 10), None)
if not cuota:
    print('No hay cuotas pendientes para la demostración.')
    sys.exit(1)
cid, alumno, saldo = cuota['id'], cuota['alumnoId'], float(cuota['saldo'])
monto = round(min(10.0, saldo), 2)
clave = f'DEMO-{int(time.time())}'
cuerpo = {'alumnoId': alumno, 'pagadorNombre': 'Demostración', 'montoTotal': monto, 'medio': 'EFECTIVO', 'idempotencyKey': clave,
          'tipoComprobante': 'RECIBO', 'detalles': [{'cuotaId': cid, 'montoAplicado': monto}]}

def saldo_actual():
    c = call('GET', f'/api/v1/pagos/alumnos/{alumno}/estado-cuenta', 'admin')[1]
    return float(next(x for x in c['cuotas'] if x['id'] == cid)['saldo'])

print(f'\n-- 1. COBRAR S/ {monto} de la cuota {cid} (saldo antes: {saldo_actual()})')
p1 = R.ok('POST /pagos (pago + detalle + comprobante + outbox en una transacción)', call('POST', '/api/v1/pagos', 'cajero', cuerpo), (200, 201))
print(f'   pago {p1["id"]} comprobante {p1["comprobante"]["numeroCompleto"]} | saldo ahora: {saldo_actual()}')

print('\n-- 2. IDEMPOTENCIA: reintento con la misma clave')
p2 = R.ok('POST /pagos (misma idempotencyKey)', call('POST', '/api/v1/pagos', 'cajero', cuerpo), (200, 201))
print(f'   mismo pago: {p2["id"] == p1["id"]} | mismo comprobante: {p2["comprobante"]["numeroCompleto"] == p1["comprobante"]["numeroCompleto"]} | saldo sigue: {saldo_actual()}')

print('\n-- 3. VALIDACIÓN: cobrar más que el saldo')
malo = dict(cuerpo, idempotencyKey=clave + '-x', montoTotal=saldo + 100, detalles=[{'cuotaId': cid, 'montoAplicado': saldo + 100}])
s, d = call('POST', '/api/v1/pagos', 'cajero', malo)
print(f'   HTTP {s}: {d.get("mensaje") if isinstance(d, dict) else d} | saldo intacto: {saldo_actual()}')
if s not in (409, 422):
    R.fallos.append('el sobrepago no fue rechazado')

print('\n-- 4. COMPENSAR: anular el pago')
R.ok('PATCH /pagos/{id}/anular', call('PATCH', f'/api/v1/pagos/{p1["id"]}/anular', 'admin', {'motivo': 'Demostración del proceso transaccional'}))
print(f'   saldo restituido: {saldo_actual()} (era {saldo}) | el evento pago.anulado avisa a matrícula y notificaciones')
sys.exit(R.fin())
