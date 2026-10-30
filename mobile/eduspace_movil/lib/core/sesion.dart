import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api.dart';

enum Perfil { docente, alumno, apoderado, otro }

/// Quién ha iniciado sesión. Guarda los tokens en el dispositivo y cierra la sesión cuando el servidor deja de reconocerla.
class Sesion extends ChangeNotifier {
  static const _kToken = 'eduspace_token';
  static const _kRefresh = 'eduspace_refresh';
  static const _kCambiar = 'eduspace_cambiar_clave';

  final Api api;
  Sesion(this.api) {
    api.token = () => _token;
    api.renovar = renovar;
    api.sesionPerdida = cerrar;
    api.estadoServicio = (caido) {
      if (caido != servicioCaido) {
        servicioCaido = caido;
        notifyListeners();
      }
    };
  }

  String? _token;
  String? _refresh;
  String usuario = '';
  int usuarioId = 0;
  List<String> roles = const [];
  bool debeCambiarClave = false;
  bool servicioCaido = false;
  /// Por qué se volvió al ingreso sin pedirlo (`vencida` o `invalida`).
  String? motivoCierre;
  bool iniciada = false;

  bool get autenticada => _token != null;

  Perfil get perfil {
    if (roles.contains('DOCENTE')) return Perfil.docente;
    if (roles.contains('APODERADO')) return Perfil.apoderado;
    if (roles.contains('ALUMNO')) return Perfil.alumno;
    return Perfil.otro;
  }

  Future<void> iniciar() async {
    final p = await SharedPreferences.getInstance();
    _token = p.getString(_kToken);
    _refresh = p.getString(_kRefresh);
    debeCambiarClave = p.getBool(_kCambiar) ?? false;
    if (_token != null && !_leerClaims(_token!)) _token = null;
    iniciada = true;
    notifyListeners();
  }

  Future<void> login(String usuario, String clave) async {
    final r = await api.post('/api/v1/auth/login', cuerpo: {'username': usuario.trim(), 'password': clave}, conSesion: false);
    await _guardar(r as Map<String, dynamic>);
    motivoCierre = null;
    notifyListeners();
  }

  Future<void> _guardar(Map<String, dynamic> r) async {
    _token = r['access_token'] as String;
    _refresh = r['refresh_token'] as String?;
    debeCambiarClave = r['debe_cambiar_clave'] == true;
    _leerClaims(_token!);
    final p = await SharedPreferences.getInstance();
    await p.setString(_kToken, _token!);
    if (_refresh != null) await p.setString(_kRefresh, _refresh!);
    await p.setBool(_kCambiar, debeCambiarClave);
  }

  bool _leerClaims(String token) {
    try {
      final partes = token.split('.');
      if (partes.length != 3) return false;
      final claims = jsonDecode(utf8.decode(base64Url.decode(base64Url.normalize(partes[1])))) as Map<String, dynamic>;
      usuario = (claims['preferred_username'] ?? '') as String;
      usuarioId = ((claims['usuarioId'] ?? claims['sub'] ?? 0) as num).toInt();
      final ra = claims['realm_access'];
      roles = ra is Map && ra['roles'] is List ? List<String>.from(ra['roles'] as List) : const [];
      return true;
    } on Object {
      return false;
    }
  }

  Future<bool>? _renovando;

  /// Renueva el token (una sola petición a la vez). `false` si ya no se puede.
  Future<bool> renovar() => _renovando ??= _renovar().whenComplete(() => _renovando = null);

  Future<bool> _renovar() async {
    if (_refresh == null) return false;
    try {
      final r = await api.post('/api/v1/auth/refresh', cuerpo: {'refresh_token': _refresh}, conSesion: false);
      await _guardar(r as Map<String, dynamic>);
      return true;
    } on Object {
      return false;
    }
  }

  Future<void> cambiarClave(String actual, String nueva) async {
    await api.post('/api/v1/auth/cambiar-clave', cuerpo: {'claveActual': actual, 'claveNueva': nueva});
    await cerrar(null);   // cambiar la clave cierra las demás sesiones: se vuelve a ingresar
  }

  Future<void> salir() async {
    final r = _refresh;
    if (r != null) {
      try {
        await api.post('/api/v1/auth/logout', cuerpo: {'refresh_token': r}, conSesion: false);
      } on Object {
        // sin conexión: igual se cierra localmente
      }
    }
    await cerrar(null);
  }

  Future<void> cerrar(String? motivo) async {
    if (_token == null && motivo != null) return; // ya estaba cerrada (varias respuestas 401 a la vez)
    _token = null;
    _refresh = null;
    debeCambiarClave = false;
    motivoCierre = motivo;
    servicioCaido = false;
    final p = await SharedPreferences.getInstance();
    // Nada de la cuenta queda en el dispositivo: sólo se conserva la dirección del servidor.
    for (final k in p.getKeys().where((k) => k != 'eduspace_servidor').toList()) {
      await p.remove(k);
    }
    usuario = '';
    usuarioId = 0;
    roles = const [];
    notifyListeners();
  }

  void claveCambiada() {
    debeCambiarClave = false;
    notifyListeners();
  }
}
