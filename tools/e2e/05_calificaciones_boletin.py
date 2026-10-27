from lib import *
ids = json.load(open('ids.json'))
SEC_ID, CURSO, PERIODO, ANIO = ids['sec'], ids['curso'], ids['periodos'][3], ids['anio']
B = '/api/v1/calificaciones'
print('== CALIFICACIONES: notas de tarea/examen llegaron por Kafka sin criterio -> pendientes (persistentes)')
pend = esperar(lambda: (lambda r: r if len(r)>=2 else None)(call('cal','GET',f"{B}/pendientes-asignar?seccionId={SEC_ID}&cursoId={CURSO}", DOC)), '2 pendientes')
print('  ', [(p['origen'], p['alumnoId'], p['valor'], p['motivo']) for p in (pend or [])])
print('== criterios (suma 100)')
ct = call('cal','POST',f"{B}/criterios", DOC, {'anioId':ANIO,'periodoId':PERIODO,'seccionId':SEC_ID,'cursoId':CURSO,'nombre':'Tareas','peso':30,'origenTipo':'TAREAS'}, expect=201)
ce = call('cal','POST',f"{B}/criterios", DOC, {'anioId':ANIO,'periodoId':PERIODO,'seccionId':SEC_ID,'cursoId':CURSO,'nombre':'Exámenes','peso':30,'origenTipo':'EXAMENES'}, expect=201)
call('cal','POST',f"{B}/criterios", DOC, {'anioId':ANIO,'periodoId':PERIODO,'seccionId':SEC_ID,'cursoId':CURSO,'nombre':'X','peso':50,'origenTipo':'MANUAL'}, expect=400)
cp = call('cal','POST',f"{B}/criterios", DOC, {'anioId':ANIO,'periodoId':PERIODO,'seccionId':SEC_ID,'cursoId':CURSO,'nombre':'Práctica','peso':40,'origenTipo':'MANUAL'}, expect=201)
call('cal','POST',f"{B}/criterios", (6,'DOCENTE'), {'anioId':ANIO,'periodoId':PERIODO,'seccionId':SEC_ID,'cursoId':CURSO,'nombre':'Y','peso':1,'origenTipo':'MANUAL'}, expect=403)
v = call('cal','GET',f"{B}/criterios/validar?periodoId={PERIODO}&seccionId={SEC_ID}&cursoId={CURSO}", DOC)
check(v['esValido'], 'pesos suman 100')
pend2 = call('cal','GET',f"{B}/pendientes-asignar?seccionId={SEC_ID}&cursoId={CURSO}", DOC)
check(len(pend2)==0, 'al crear los criterios, las notas pendientes se incorporaron')
reg = call('cal','GET',f"{B}/registro?seccionId={SEC_ID}&cursoId={CURSO}&periodoId={PERIODO}", DOC)
print('  registro:', json.dumps(reg)[:500])
print('== notas manuales en lote + promedio')
mats = {a['alumnoId']: a['matriculaId'] for a in call('mat','GET',f"/api/v1/matriculas/seccion/{SEC_ID}/alumnos", SEC)}
notas = [{'matriculaId':mats[a],'alumnoId':a,'valor':v} for a,v in [(1,16),(2,12)]]
call('cal','PUT',f"{B}/notas/lote", DOC, {'seccionId':SEC_ID,'cursoId':CURSO,'periodoId':PERIODO,'criterioId':cp['id'],'notas':notas})
# el alumno 2 no tiene tarea ni examen: se completan a mano para poder cerrar
for crit in (ct, ce):
    call('cal','PUT',f"{B}/notas/lote", DOC, {'seccionId':SEC_ID,'cursoId':CURSO,'periodoId':PERIODO,'criterioId':crit['id'],'notas':[{'matriculaId':mats[2],'alumnoId':2,'valor':11}]})
prom = call('cal','GET',f"{B}/promedios?seccionId={SEC_ID}&periodoId={PERIODO}", DOC)
print('  ', [(p['alumnoNombre'], p['promedio'], p['literal'], p['estado']) for p in prom])
a1 = [p for p in prom if p['alumnoId']==1][0]
check(abs(a1['promedio']-(17.5*.3+20*.3+16*.4))<0.01, f"promedio ponderado alumno 1 = {17.5*.3+20*.3+16*.4:.2f} (obtenido {a1['promedio']})")
print('== cierre del periodo y boletín')
call('cal','POST',f"{B}/periodos/cerrar", SEC, {'seccionId':SEC_ID,'periodoId':PERIODO})
call('cal','PUT',f"{B}/notas/lote", DOC, {'seccionId':SEC_ID,'cursoId':CURSO,'periodoId':PERIODO,'criterioId':cp['id'],'notas':notas}, expect=400)
bol = call('cal','POST',f"{B}/boletines/generar", SEC, {'seccionId':SEC_ID,'periodoId':PERIODO,'anioId':ANIO})
print('  ', [(b['alumnoId'], b['version'], b['rutaPdf']) for b in bol])
u = call('cal','GET',f"{B}/boletines/{bol[0]['id']}/url", APO if bol[0]['alumnoId']==1 else SEC)
b1 = [b for b in bol if b['alumnoId']==1][0]
u = call('cal','GET',f"{B}/boletines/{b1['id']}/url", APO)
try:
    with urllib.request.urlopen(u['urlDescarga']) as rr:
        data = rr.read(); check(data.startswith(b'%PDF'), f'boletín descargado desde MinIO es PDF ({len(data)} bytes)')
        open('boletin.pdf','wb').write(data)
except Exception as ex: check(False, f'descarga boletín: {ex}')
b2b = [b for b in bol if b['alumnoId']==2][0]
call('cal','GET',f"{B}/boletines/{b2b['id']}", APO, expect=403)
bol2 = call('cal','POST',f"{B}/boletines/generar", SEC, {'seccionId':SEC_ID,'periodoId':PERIODO,'anioId':ANIO})
check(all(b['version']==2 for b in bol2), 'segunda emisión = versión 2')
print('  boletin.generado:', [(e['alumnoId'], e['periodoNombre']) for e in topic('boletin.generado')][:4])
print('== libretas')
call('cal','GET',f"{B}/mias", APO)
call('cal','GET',f"{B}/alumnos/1/libreta", ALU)
call('cal','GET',f"{B}/alumnos/2/libreta", ALU, expect=403)
call('cal','GET',f"{B}/alumnos/2/libreta", APO, expect=403)
print('  resultados anuales:', [(r['alumnoId'], r['promedioFinal'], r['estado']) for r in call('cal','GET',f"{B}/resultados-anuales?seccionId={SEC_ID}", SEC)])
print('== reapertura (solo ADMIN, con motivo)')
call('cal','POST',f"{B}/periodos/reabrir", SEC, {'seccionId':SEC_ID,'periodoId':PERIODO,'motivo':'x'}, expect=403)
call('cal','POST',f"{B}/periodos/reabrir", ADM, {'seccionId':SEC_ID,'periodoId':PERIODO,'motivo':'Corrección de nota'})
print('FAILS:', FAILS)
