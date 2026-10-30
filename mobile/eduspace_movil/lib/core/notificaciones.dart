import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api.dart';

/// Bandeja de notificaciones. Sin Firebase no hay «push» real: la app consulta el contador cada 45 s mientras está abierta
/// y al volver a primer plano.
class Notificaciones extends ChangeNotifier {
  final Api api;
  Notificaciones(this.api);
  int noLeidas = 0;
  Timer? _t;

  void iniciar() {
    detener();
    refrescar();
    _t = Timer.periodic(const Duration(seconds: 45), (_) => refrescar());
  }

  void detener() {
    _t?.cancel();
    _t = null;
    noLeidas = 0;
  }

  Future<void> refrescar() async {
    try {
      final r = await api.get('/api/v1/notificaciones/mias/no-leidas/conteo');
      final n = (r['noLeidas'] as num?)?.toInt() ?? 0;
      if (n != noLeidas) {
        noLeidas = n;
        notifyListeners();
      }
    } on Object {
      // silencioso: es sólo el contador
    }
  }

  Future<Map<String, dynamic>> pagina(int pagina, {bool? leida}) async =>
      Map<String, dynamic>.from(await api.get('/api/v1/notificaciones/mias', query: {'page': pagina, 'size': 20, 'leida': leida}));

  Future<void> marcar(int id) async {
    await api.patch('/api/v1/notificaciones/$id/leida');
    refrescar();
  }

  Future<void> marcarTodas() async {
    await api.patch('/api/v1/notificaciones/mias/leer-todas');
    noLeidas = 0;
    notifyListeners();
  }
}
