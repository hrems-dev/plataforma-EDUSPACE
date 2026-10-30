import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'config.dart';

/// Error de la API con un mensaje que se puede mostrar a la persona.
class ApiException implements Exception {
  final int status;
  final String mensaje;
  ApiException(this.status, this.mensaje);
  @override
  String toString() => mensaje;
}

/// El servidor no responde (se reinició, está caído o el Gateway no encuentra el servicio).
class ServicioCaido extends ApiException {
  ServicioCaido() : super(0, 'No pudimos conectar con el sistema. Puede que se esté reiniciando; intenta de nuevo en un momento.');
}

/// La sesión ya no sirve (token vencido o el sistema no lo reconoce).
class SesionInvalida extends ApiException {
  SesionInvalida() : super(401, 'Tu sesión terminó. Inicia sesión de nuevo.');
}

/// Cliente HTTP del Gateway: agrega el token y `X-Trace-ID`, renueva el token una vez si vence y distingue
/// «sesión inválida» de «servicio caído» para que la app pueda mandar al ingreso o mostrar el aviso con «Reintentar».
class Api {
  /// Lo conecta la sesión: devuelve el token vigente o `null`.
  String? Function() token = () => null;

  /// Renueva el token; devuelve `true` si lo logró.
  Future<bool> Function() renovar = () async => false;

  /// Se llama cuando la sesión no se pudo recuperar.
  void Function(String motivo) sesionPerdida = (_) {};

  /// Se llama con `true` cuando el servidor no responde y con `false` cuando vuelve.
  void Function(bool caido) estadoServicio = (_) {};

  final _azar = Random();
  http.Client _cliente = http.Client();

  @visibleForTesting
  void usarCliente(http.Client c) => _cliente = c;

  String _traza() => List.generate(16, (_) => _azar.nextInt(256).toRadixString(16).padLeft(2, '0')).join();

  Uri _uri(String ruta, [Map<String, dynamic>? q]) {
    final limpio = <String, String>{};
    q?.forEach((k, v) {
      if (v != null) limpio[k] = '$v';
    });
    return Uri.parse('${Config.baseUrl}$ruta').replace(queryParameters: limpio.isEmpty ? null : limpio);
  }

  Future<dynamic> get(String ruta, {Map<String, dynamic>? query}) => _enviar('GET', ruta, query: query);
  Future<dynamic> post(String ruta, {Object? cuerpo, bool conSesion = true}) => _enviar('POST', ruta, cuerpo: cuerpo, conSesion: conSesion);
  Future<dynamic> put(String ruta, {Object? cuerpo}) => _enviar('PUT', ruta, cuerpo: cuerpo);
  Future<dynamic> patch(String ruta, {Object? cuerpo}) => _enviar('PATCH', ruta, cuerpo: cuerpo);
  Future<dynamic> delete(String ruta) => _enviar('DELETE', ruta);

  Future<dynamic> _enviar(String metodo, String ruta, {Map<String, dynamic>? query, Object? cuerpo, bool conSesion = true, bool reintento = false}) async {
    final cab = <String, String>{'Accept': 'application/json', 'X-Trace-ID': _traza()};
    if (cuerpo != null) cab['Content-Type'] = 'application/json';
    final t = conSesion ? token() : null;
    if (t != null) cab['Authorization'] = 'Bearer $t';
    final req = http.Request(metodo, _uri(ruta, query))..headers.addAll(cab);
    if (cuerpo != null) req.body = jsonEncode(cuerpo);

    http.Response r;
    try {
      r = await http.Response.fromStream(await _cliente.send(req).timeout(const Duration(seconds: 20)));
    } on Object {
      estadoServicio(true);
      throw ServicioCaido();
    }
    final texto = utf8.decode(r.bodyBytes);
    dynamic json;
    if (texto.trim().isNotEmpty && (texto.trim().startsWith('{') || texto.trim().startsWith('['))) {
      try {
        json = jsonDecode(texto);
      } on Object {
        json = null;
      }
    }

    // 502/503/504 o un 500 «crudo» del Gateway (sin `mensaje`): el servicio no está disponible.
    final crudo500 = r.statusCode == 500 && !(json is Map && json['mensaje'] is String);
    if ([502, 503, 504].contains(r.statusCode) || crudo500) {
      estadoServicio(true);
      throw ServicioCaido();
    }
    estadoServicio(false);

    if (r.statusCode == 401 && conSesion) {
      if (!reintento && await renovar()) return _enviar(metodo, ruta, query: query, cuerpo: cuerpo, reintento: true);
      sesionPerdida(reintento ? 'invalida' : 'vencida');
      throw SesionInvalida();
    }
    if (r.statusCode >= 200 && r.statusCode < 300) return json ?? (texto.isEmpty ? null : texto);

    final detalles = json is Map ? json['detalles'] : null;
    final msg = detalles is List && detalles.isNotEmpty
        ? detalles.take(3).join('. ')
        : (json is Map ? (json['mensaje'] ?? json['message'] ?? json['error']) : null);
    throw ApiException(r.statusCode, msg is String && msg.isNotEmpty ? msg : _porDefecto(r.statusCode));
  }

  String _porDefecto(int s) => switch (s) {
        400 => 'Los datos enviados no son válidos.',
        403 => 'No tienes permisos para esta acción.',
        404 => 'No encontramos lo que buscas.',
        409 => 'La operación no se pudo completar.',
        423 => 'La cuenta está bloqueada unos minutos por intentos fallidos.',
        429 => 'Demasiados intentos seguidos. Espera un momento.',
        _ => 'Ocurrió un error ($s).',
      };

  /// Descarga binaria (PDF de boletín, etc.) con la misma sesión.
  Future<Uint8List> bytes(String ruta) async {
    final r = await _cliente.get(_uri(ruta), headers: {'Authorization': 'Bearer ${token() ?? ''}'});
    if (r.statusCode != 200) throw ApiException(r.statusCode, 'No se pudo descargar el archivo.');
    return r.bodyBytes;
  }

  /// Sube bytes a una URL firmada (MinIO); no lleva el token del sistema.
  Future<void> subir(String url, Uint8List datos, String contentType) async {
    try {
      final r = await _cliente.put(Uri.parse(url), headers: {'Content-Type': contentType}, body: datos).timeout(const Duration(minutes: 2));
      if (r.statusCode < 200 || r.statusCode >= 300) throw ApiException(r.statusCode, 'No se pudo subir el archivo.');
    } on ApiException {
      rethrow;
    } on Object {
      throw ApiException(0, 'No se pudo subir el archivo. Revisa tu conexión.');
    }
  }
}
