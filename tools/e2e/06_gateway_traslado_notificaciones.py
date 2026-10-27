from lib import *
ids = json.load(open('ids.json')); SEC_ID=ids['sec']
print('== Gateway (18080) enruta con JWT hacia cada servicio')
for path in ['/api/v1/matriculas/mias','/api/v1/asistencia/mias','/api/v1/tareas/mias','/api/v1/calificaciones/mias']:
    call('gw','GET',path, APO)
call('gw','GET','/api/v1/pagos/mias/cuotas', APO)
call('gw','GET','/api/v1/examenes/mias', ALU)
call('gw','GET','/api/v1/matriculas/mias', None, expect=401)
call('gw','GET','/internal/matriculas/seccion/1', ADM, expect=404)   # /internal no se expone por el Gateway
print('== Traslado de sección y retiro')
cb = call('mat','GET',f"/api/v1/matriculas?seccionId={SEC_ID}", SEC)['contenido']
m2 = [m for m in cb if m['alumnoId']==2][0]
tr = call('mat','PATCH',f"/api/v1/matriculas/{m2['id']}/traslado", SEC, {'nuevaSeccionId':ids['sec_b'],'motivo':'Cambio de turno'})
check(tr and tr['seccionId']==ids['sec_b'] and tr['estado']=='MATRICULADO', 'traslado conserva MATRICULADO')
esperar(lambda: subprocess.run(['podman','exec','e2e-pg-asistencia','psql','-U','eduspace','-d','asistencia_db','-tAc',"select seccion_id from matriculas_local where alumno_id = 2"],capture_output=True,text=True).stdout.strip()==str(ids['sec_b']), 'réplica de asistencia con la nueva sección')
call('mat','PATCH',f"/api/v1/matriculas/{m2['id']}/retiro", SEC, {'motivo':'Mudanza'})
esperar(lambda: [c for c in call('pago','GET','/api/v1/pagos/cuotas?alumnoId=2&size=50', CAJ)['contenido'] if c['estado']=='ANULADA'], 'pago-ms anula las cuotas del alumno retirado')
anuladas = [c['estado'] for c in call('pago','GET','/api/v1/pagos/cuotas?alumnoId=2&size=50', CAJ)['contenido']]
cuenta = {e: anuladas.count(e) for e in set(anuladas)}
print('  estados de cuotas alumno 2:', cuenta)
check(cuenta.get('ANULADA')==10 and cuenta.get('PAGADA')==1 and 'PENDIENTE' not in cuenta, 'retiro: 10 pensiones ANULADAS, la matrícula pagada se conserva, ninguna pendiente')
print('== Alertas administrativas / cupos')
call('pago','GET','/api/v1/pagos/alertas', ADM)
call('mat','GET',f"/api/v1/matriculas/cupos?anioId={ids['anio']}&gradoId=6", SEC)
print('== Notificaciones (Hermes) generadas por los eventos de Witmer y Andre')
out = subprocess.run(['podman','exec','e2e-pg-notificaciones','psql','-U','eduspace','-d','notificaciones_db','-tAc',"select tipo||' | '||canal||' | '||estado||' | '||titulo from notificaciones order by id"],capture_output=True,text=True)
print(out.stdout[:900] or out.stderr[:300])
import urllib.request as u
d = json.loads(u.urlopen('http://localhost:18025/api/v1/messages').read())
print('  correos en Mailpit:', d['total'], sorted({m['Subject'] for m in d['messages']}))
print('FAILS:', FAILS)
