from lib import *
hoy = datetime.date.today()
print('== planificación')
anio = call('plan','POST','/api/v1/planificacion/anios', ADM, {'nombre':'2026','fechaInicio':'2026-03-01','fechaFin':'2026-12-20'})
call('plan','PATCH',f"/api/v1/planificacion/anios/{anio['id']}/activar", ADM)
pids = []
for n,(a,b) in enumerate([('2026-03-01','2026-05-15'),('2026-05-16','2026-07-31'),('2026-08-01','2026-09-30'),('2026-10-01','2026-12-20')],1):
    p = call('plan','POST','/api/v1/planificacion/periodos', ADM, {'anioId':anio['id'],'numero':n,'nombre':f'{n} Bimestre','fechaInicio':a,'fechaFin':b})
    pids.append(p['id'])
call('plan','PATCH',f"/api/v1/planificacion/periodos/{pids[3]}/estado", ADM, {'estado':'ACTIVO'})
curso = call('plan','POST','/api/v1/planificacion/cursos', ADM, {'codigo':'MAT','nombre':'Matemática','area':'Matemática'})
doc = call('plan','POST','/api/v1/planificacion/docentes', ADM, {'numeroDocumento':'40123456','nombres':'Luis','apellidos':'Díaz Ramos','email':'ldiaz@colegio.pe','especialidad':'Matemática'})
call('plan','PATCH',f"/api/v1/planificacion/docentes/{doc['id']}/usuario", ADM, {'usuarioId':uid(DOC)})
sec = call('plan','POST','/api/v1/planificacion/secciones', ADM, {'anioId':anio['id'],'gradoId':6,'nombre':'A','turno':'MANANA','capacidad':2,'tutorDocenteId':doc['id']})
sec_b = call('plan','POST','/api/v1/planificacion/secciones', ADM, {'anioId':anio['id'],'gradoId':6,'nombre':'B','turno':'MANANA','capacidad':30})
call('plan','POST','/api/v1/planificacion/malla', ADM, {'gradoId':6,'cursoId':curso['id'],'horasSemanales':5})
asig = call('plan','POST','/api/v1/planificacion/asignaciones', ADM, {'seccionId':sec['id'],'cursoId':curso['id'],'docenteId':doc['id']})
print('== alumnos')
def alumno(dni, nombre, ap, dniap, usuario_ap=None):
    return call('alum','POST','/api/v1/alumnos', SEC, {'numeroDocumento':dni,'nombres':nombre,'apellidoPaterno':ap,'apellidoMaterno':'Soto','fechaNacimiento':'2017-04-10','sexo':'M',
        'apoderados':[{'apoderado':{'numeroDocumento':dniap,'nombres':'Rosa','apellidos':ap+' Vega','telefono':'987654321','email':'rosa@correo.pe'},'parentesco':'MADRE','esPrincipal':True,'esResponsablePago':True}]})
al = []
for i,(dni,n,ap,dap) in enumerate([('70000001','Mateo','Quispe','10000001'),('70000002','Valeria','Huamán','10000002'),('70000003','Diego','Torres','10000003')]):
    a = alumno(dni,n,ap,dap)
    al.append(a)
    # expediente completo: 3 documentos verificados
    for t in ['PARTIDA_NACIMIENTO','DNI','FICHA_SIAGIE']:
        import http.client, uuid
        b = uuid.uuid4().hex
        body = (f'--{b}\r\nContent-Disposition: form-data; name="archivo"; filename="{t}.pdf"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4 prueba\r\n--{b}--\r\n').encode()
        c = http.client.HTTPConnection('localhost', 8092)
        c.request('POST', f"/api/v1/alumnos/{a['id']}/documentos?tipo={t}", body, {'Authorization':'Bearer '+tok(*SEC),'Content-Type':f'multipart/form-data; boundary={b}'})
        r = c.getresponse(); txt = r.read().decode()
        d = json.loads(txt) if r.status < 300 else None
        print('  ', 'OK ' if r.status < 300 else 'ERR', 'doc', t, r.status, '' if d else txt[:200])
        if d: call('alum','PATCH',f"/api/v1/alumnos/{a['id']}/documentos/{d['id']}/verificado", SEC, {'verificado':True})
call('alum','PATCH',f"/api/v1/alumnos/{al[0]['id']}/usuario", ADM, {'usuarioId':uid(ALU)})
ap0 = call('alum','GET',f"/api/v1/alumnos/{al[0]['id']}", SEC)
print(json.dumps(ap0)[:600])
json.dump({'anio':anio['id'],'periodos':pids,'curso':curso['id'],'doc':doc['id'],'sec':sec['id'],'sec_b':sec_b['id'],'alumnos':[a['id'] for a in al]}, open('ids.json','w'))
print('FAILS:', FAILS)
