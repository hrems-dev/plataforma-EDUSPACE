#!/usr/bin/env python3
"""Deja lista la matrícula en línea para volver a hacerla (la primera vez o las veces que haga falta) con datos que no choquen.

  python3 tools/demo/preparar_matricula_en_linea.py            # limpia pruebas, revisa campaña y vacantes, y da datos NUEVOS para teclear
  python3 tools/demo/preparar_matricula_en_linea.py --probar   # además hace una matrícula completa de prueba para comprobar que todo responde

Por qué hace falta: el documento del alumno es único. Si se repite el de una matrícula anterior el sitio responde «ya hay un alumno
registrado con ese documento». Cada ejecución entrega un alumno y un apoderado con documentos que nadie usó, un correo de prueba
(se lee en Mailpit, http://localhost:18025) y el grado con más vacantes. Además libera los cupos de las matrículas de prueba anteriores.
"""
import json, os, subprocess, sys, urllib.parse, urllib.request, urllib.error

GW = os.environ.get('GATEWAY', 'http://localhost:18080')
AQUI = os.path.dirname(os.path.abspath(__file__))
CONTADOR = os.path.join(AQUI, '.matricula_demo_n')
NOMBRES = [('Valeria', 'Camacho', 'Soto', 'F'), ('Mateo', 'Salinas', 'Ruiz', 'M'), ('Renata', 'Ibarra', 'León', 'F'), ('Thiago', 'Paredes', 'Cruz', 'M'),
           ('Isabella', 'Montes', 'Vega', 'F'), ('Joaquín', 'Rivas', 'Luna', 'M'), ('Camila', 'Herrera', 'Díaz', 'F'), ('Santiago', 'Poma', 'Cano', 'M')]
APODERADOS = [('Rosa', 'Camacho Reyes', 'MADRE'), ('Luis', 'Salinas Mora', 'PADRE'), ('Elena', 'Ibarra Gómez', 'MADRE'), ('Jorge', 'Paredes Lara', 'PADRE'),
              ('Marta', 'Montes Peña', 'MADRE'), ('Carlos', 'Rivas Soto', 'PADRE'), ('Ana', 'Herrera Bravo', 'MADRE'), ('Pedro', 'Poma Ríos', 'PADRE')]

def http(m, ruta, tok=None, cuerpo=None, token_tramite=None):
    h = {}
    if cuerpo is not None: h['Content-Type'] = 'application/json'
    if tok: h['Authorization'] = 'Bearer ' + tok
    if token_tramite: h['X-Matricula-Token'] = token_tramite
    r = urllib.request.Request(GW + ruta, data=json.dumps(cuerpo).encode() if cuerpo is not None else None, headers=h, method=m)
    try:
        with urllib.request.urlopen(r, timeout=40) as x:
            t = x.read().decode(); return x.status, (json.loads(t) if t.strip()[:1] in ('{', '[') else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try: return e.code, json.loads(t)
        except ValueError: return e.code, t
    except OSError as e:
        sys.exit(f'No llega al sistema ({GW}): {e}')

def paso(msg, ok=True):
    print(('  ✔ ' if ok else '  ✘ ') + msg)
    if not ok: sys.exit(1)

s, d = http('POST', '/api/v1/auth/login', cuerpo={'username': 'admin', 'password': 'admin123'})
if s != 200: sys.exit('No se pudo entrar como admin (admin/admin123): ¿el sistema está en modo login propio? (tools/e2e/modo.sh propio)')
adm = d['access_token']

print('== 1. Limpiar matrículas de pruebas anteriores (libera cupos y cuotas)')
subprocess.run([sys.executable, os.path.join(AQUI, 'limpiar_pruebas.py')], check=False)

print('== 2. Campaña de matrícula')
s, anio = http('GET', '/api/v1/planificacion/anios/activo', adm)
s, camps = http('GET', f"/api/v1/matriculas/campanias?anioId={anio['id']}", adm)
abierta = next((c for c in camps if c['estado'] == 'ABIERTA'), None)
if not abierta:
    plan = next((c for c in camps if c['estado'] == 'PLANIFICADA'), None)
    if plan:
        s, _ = http('PATCH', f"/api/v1/matriculas/campanias/{plan['id']}/abrir", adm)
        paso(f"campaña «{plan['nombre']}» abierta", s in (200, 204))
    else:
        paso('no hay campaña ABIERTA ni PLANIFICADA: créala en Matrícula → Campañas', False)
else:
    paso(f"campaña «{abierta['nombre']}» ABIERTA hasta {abierta['fechaFin']}")

print('== 3. Vacantes y costos que verá la familia')
s, oferta = http('GET', '/api/v1/publico/matricula/oferta')
grados = [(n['nombre'], g) for n in oferta.get('niveles', []) for g in n['grados'] if g['vacantes'] > 0 and g.get('matricula')]
if not grados: paso('ningún grado tiene vacantes y tarifa de matrícula', False)
nivel, grado = max(grados, key=lambda x: x[1]['vacantes'])
paso(f"{grado['nombre']} ({nivel}): {grado['vacantes']} vacantes · matrícula S/ {grado['matricula']}")

print('== 4. Datos nuevos para la matrícula en línea')
n = int(open(CONTADOR).read()) + 1 if os.path.exists(CONTADOR) else int(__import__('time').time()) % 100000
while True:
    doc_a, doc_p = f'76{n % 1000000:06d}', f'46{n % 1000000:06d}'
    s, r = http('GET', '/api/v1/alumnos?texto=' + doc_a + '&size=1', adm)
    if not (isinstance(r, dict) and r.get('contenido')): break
    n += 1
open(CONTADOR, 'w').write(str(n))
nom, ap1, ap2, sexo = NOMBRES[n % len(NOMBRES)]
pnom, pape, parentesco = APODERADOS[n % len(APODERADOS)]
correo = f'familia.{doc_p}@ejemplo.pe'
datos = {'doc_a': doc_a, 'nom': nom, 'ap1': ap1, 'ap2': ap2, 'sexo': sexo, 'doc_p': doc_p, 'pnom': pnom, 'pape': pape, 'correo': correo, 'grado': grado}

if '--probar' in sys.argv:
    print('== 5. Prueba completa (reserva + pago con 4242…)')
    sol = {'gradoId': grado['id'], 'acepto': True, 'sitio': '',
           'alumno': {'tipoDocumento': 'DNI', 'numeroDocumento': doc_a, 'nombres': nom, 'apellidoPaterno': ap1, 'apellidoMaterno': ap2, 'fechaNacimiento': '2018-04-12', 'sexo': sexo},
           'apoderado': {'tipoDocumento': 'DNI', 'numeroDocumento': doc_p, 'nombres': pnom, 'apellidos': pape, 'telefono': '999111222', 'email': correo, 'parentesco': parentesco}}
    s, ini = http('POST', '/api/v1/publico/matricula', cuerpo=sol)
    paso(f"reserva creada {ini.get('referencia')} en la sección {ini.get('seccion')}" if s == 201 else f'la reserva falló ({s}): {ini}', s == 201)
    s, res = http('POST', f"/api/v1/publico/matricula/{ini['referencia']}/pagar", cuerpo={'tarjeta': {'numero': '4242 4242 4242 4242', 'titular': 'PRUEBA', 'vencimiento': '12/30', 'cvv': '123'}}, token_tramite=ini['token'])
    paso(f"pago aceptado: el trámite queda «{res.get('estado')}» (en revisión del colegio)" if s == 200 else f'el pago falló ({s}): {res}', s == 200)
    s, m = http('GET', f"/api/v1/matriculas?texto={ini['referencia']}", adm)
    mid = m['contenido'][0]['id'] if s == 200 and m.get('contenido') else None
    if mid:
        s, _ = http('PATCH', f'/api/v1/matriculas/{mid}/rechazar', adm, {'motivo': 'Prueba de la matrícula en línea (se libera el cupo)'})
        paso(f"la matrícula de prueba {ini['referencia']} se rechazó: el cupo vuelve a estar libre", s == 200)
    else:
        paso('no se encontró la matrícula de prueba para liberarla', False)
    # los de la prueba ya están usados: se entregan otros nuevos
    n += 1; open(CONTADOR, 'w').write(str(n))
    doc_a, doc_p = f'76{n % 1000000:06d}', f'46{n % 1000000:06d}'
    nom, ap1, ap2, sexo = NOMBRES[n % len(NOMBRES)]; pnom, pape, parentesco = APODERADOS[n % len(APODERADOS)]; correo = f'familia.{doc_p}@ejemplo.pe'

print(f"""
=== DATOS PARA TECLEAR EN http://localhost:4200/matricula-en-linea (o la IP del equipo) ===
  Grado ............ {grado['nombre']}  ({grado['vacantes']} vacantes)
  ALUMNO ........... {nom} {ap1} {ap2} · DNI {doc_a} · sexo {sexo} · nacimiento 12/04/2018
  APODERADO ........ {pnom} {pape} · DNI {doc_p} · teléfono 999111222 · {parentesco.lower()}
  Correo ........... {correo}   (el correo se lee en Mailpit: http://localhost:18025)
  Tarjeta .......... 4242 4242 4242 4242 · 12/30 · 123   (4000 0000 0000 0002 simula un rechazo)
  Después: Matrícula → Por aprobar (admin) → Aprobar.  Usuario del alumno: {nom.lower().replace('í','i').replace('ú','u')}.{ap1.lower()}
""")
