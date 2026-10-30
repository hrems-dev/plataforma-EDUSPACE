import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Dirección del Gateway de EduSpace. Se puede cambiar desde la pantalla de ingreso («Servidor»).
/// - Emulador de Android: el equipo que lo aloja es `10.0.2.2`.
/// - Celular real: la IP del equipo en la red (p. ej. `http://192.168.1.20:18080`).
class Config {
  static const _clave = 'eduspace_servidor';
  static String baseUrl = _porDefecto();

  static String _porDefecto() =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android ? 'http://10.0.2.2:18080' : 'http://localhost:18080';

  static Future<void> cargar() async {
    final p = await SharedPreferences.getInstance();
    baseUrl = p.getString(_clave) ?? _porDefecto();
  }

  static Future<void> guardar(String url) async {
    var u = url.trim();
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    baseUrl = u.isEmpty ? _porDefecto() : u;
    final p = await SharedPreferences.getInstance();
    await p.setString(_clave, baseUrl);
  }

  /// Comprueba que el Gateway responde en esa dirección. Devuelve (ok, mensaje para la persona).
  static Future<(bool, String)> probar(String url) async {
    var u = url.trim();
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    if (!u.startsWith('http://') && !u.startsWith('https://')) return (false, 'La dirección debe empezar con http:// o https://');
    try {
      final r = await http.get(Uri.parse('$u/actuator/health')).timeout(const Duration(seconds: 6));
      if (r.statusCode == 200) return (true, 'Conectado: el sistema responde.');
      if (r.statusCode == 503) return (true, 'El Gateway responde, pero algún servicio aún está arrancando.');
      return (false, 'Respondió ${r.statusCode}: revisa que sea la dirección del Gateway (puerto 18080).');
    } on Object {
      return (false, 'No hay respuesta. Revisa la IP, que estén en la misma red y que el cortafuegos permita el puerto 18080.');
    }
  }
}
