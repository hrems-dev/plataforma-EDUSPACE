#!/usr/bin/env python3
"""Retira las matrículas de pruebas (alumnos «Demostración Saga» y «Ensayo Prueba») que dejan los ensayos de la demo.

Libera los cupos y anula las cuotas pendientes, así que desaparecen de Cupos, Deuda vencida y «Vencen pronto».
No borra nada: los cobros ya hechos quedan en el historial de Finanzas (los pagos no se eliminan, solo se anulan).
"""
import json, os, urllib.parse, urllib.request, urllib.error
GW = os.environ.get('GATEWAY', 'http://localhost:18080')

def http(m, p, tok, body=None):
    r = urllib.request.Request(GW + p, data=json.dumps(body).encode() if body is not None else None, method=m,
                               headers={'Authorization': 'Bearer ' + tok, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(r, timeout=30) as x:
            t = x.read().decode(); return x.status, (json.loads(t) if t.strip()[:1] in ('{', '[') else t)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()

r = urllib.request.Request(GW + '/api/v1/auth/login', data=json.dumps({'username': 'admin', 'password': 'admin123'}).encode(), headers={'Content-Type': 'application/json'})
tok = json.load(urllib.request.urlopen(r))['access_token']
retiradas = rechazadas = 0
for texto in ('Demostración Saga', 'Ensayo Prueba'):
    s, d = http('GET', '/api/v1/matriculas?texto=' + urllib.parse.quote(texto) + '&size=100', tok)
    for m in d['contenido']:
        if m['estado'] == 'MATRICULADO':
            retiradas += http('PATCH', f"/api/v1/matriculas/{m['id']}/retiro", tok, {'motivo': 'Limpieza de pruebas de la demostración'})[0] == 200
        elif m['estado'] == 'POR_APROBAR':
            rechazadas += http('PATCH', f"/api/v1/matriculas/{m['id']}/rechazar", tok, {'motivo': 'Limpieza de pruebas de la demostración'})[0] == 200
        elif m['estado'] == 'PRE_MATRICULA':
            http('PATCH', f"/api/v1/matriculas/{m['id']}/anular", tok, {'motivo': 'Limpieza de pruebas de la demostración'})
print(f'Matrículas de prueba retiradas: {retiradas} · rechazadas: {rechazadas}')
