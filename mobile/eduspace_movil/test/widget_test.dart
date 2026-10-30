import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:eduspace_movil/core/api.dart';
import 'package:eduspace_movil/core/formato.dart';
import 'package:eduspace_movil/core/sesion.dart';
import 'package:shared_preferences/shared_preferences.dart';

String jwt(List<String> roles) {
  String b(Object o) => base64Url.encode(utf8.encode(jsonEncode(o))).replaceAll('=', '');
  return '${b({'alg': 'none'})}.${b({'preferred_username': 'ana.perez', 'usuarioId': 7, 'realm_access': {'roles': roles}})}.x';
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('formato: nombre natural y hora', () {
    expect(nombreNatural('Pérez Gómez, Ana'), 'Ana Pérez Gómez');
    expect(primerNombre('Pérez Gómez, Ana María'), 'Ana');
    expect(hora('08:30:00'), '08:30');
  });

  test('login guarda la sesión, lee roles y exige cambiar la clave', () async {
    final api = Api()..usarCliente(MockClient((r) async => http.Response(jsonEncode({'access_token': jwt(['DOCENTE']), 'refresh_token': 'r', 'debe_cambiar_clave': true}), 200, headers: {'content-type': 'application/json'})));
    final s = Sesion(api);
    await s.login('ana.perez', '12345678');
    expect(s.autenticada, true);
    expect(s.perfil, Perfil.docente);
    expect(s.usuario, 'ana.perez');
    expect(s.debeCambiarClave, true);
  });

  test('un 401 que no se puede renovar cierra la sesión con motivo «vencida»', () async {
    final api = Api()..usarCliente(MockClient((r) async {
      if (r.url.path.endsWith('/auth/login')) return http.Response(jsonEncode({'access_token': jwt(['ALUMNO']), 'refresh_token': 'r'}), 200);
      return http.Response('{}', 401);
    }));
    final s = Sesion(api);
    await s.login('a', 'b');
    await expectLater(api.get('/api/v1/alumnos/mios'), throwsA(isA<SesionInvalida>()));
    expect(s.autenticada, false);
    expect(s.motivoCierre, 'vencida');
  });

  test('un 503 se reporta como servicio caído', () async {
    final api = Api()..usarCliente(MockClient((r) async => http.Response('', 503)));
    final s = Sesion(api);
    await expectLater(api.get('/api/v1/x'), throwsA(isA<ServicioCaido>()));
    expect(s.servicioCaido, true);
  });

  test('los errores de la API muestran su mensaje', () async {
    final api = Api()..usarCliente(MockClient((r) async => http.Response(jsonEncode({'codigo': 409, 'mensaje': 'La tarea ya fue entregada'}), 409)));
    Sesion(api);
    await expectLater(api.get('/api/v1/x'), throwsA(predicate((e) => e is ApiException && e.mensaje == 'La tarea ya fue entregada')));
  });

  test('al cerrar la sesión no queda nada de la cuenta en el dispositivo (salvo el servidor)', () async {
    SharedPreferences.setMockInitialValues({'eduspace_servidor': 'http://x:1', 'eduspace_otro': 'dato'});
    final api = Api()..usarCliente(MockClient((r) async => http.Response(jsonEncode({'access_token': jwt(['ALUMNO']), 'refresh_token': 'r'}), 200)));
    final s = Sesion(api);
    await s.login('a', 'b');
    await s.cerrar(null);
    final p = await SharedPreferences.getInstance();
    expect(p.getKeys(), {'eduspace_servidor'});
    expect(s.usuario, '');
    expect(s.roles, isEmpty);
  });
}
