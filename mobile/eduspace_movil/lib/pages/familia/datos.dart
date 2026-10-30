import 'package:flutter/foundation.dart';

import '../../core/api.dart';
import '../docente/datos.dart';

/// Hijos/alumno de la sesión, con el seleccionado. Alumno: uno solo. Apoderado: uno o más.
class FamiliaDatos extends ChangeNotifier {
  final Api api;
  final bool esApoderado;
  FamiliaDatos(this.api, this.esApoderado);

  List<Map<String, dynamic>> alumnos = [];
  List<Map<String, dynamic>> matriculas = [];
  int seleccionado = 0;
  bool cargado = false;

  Map<String, dynamic>? get alumno => alumnos.isEmpty ? null : alumnos[seleccionado.clamp(0, alumnos.length - 1)];
  int? get alumnoId => ((alumno?['alumnoId'] ?? alumno?['id']) as num?)?.toInt();
  String get nombre => '${alumno?['nombreCompleto'] ?? ''}';

  Map<String, dynamic>? get matricula {
    final id = alumnoId;
    for (final m in matriculas) {
      if ((m['alumnoId'] as num?)?.toInt() == id && m['estado'] == 'MATRICULADO') return m;
    }
    return null;
  }

  Future<void> cargar() async {
    alumnos = lista(await api.get('/api/v1/alumnos/mios'));
    matriculas = lista(await api.get('/api/v1/matriculas/mias'));
    cargado = true;
    notifyListeners();
  }

  void elegir(int i) {
    seleccionado = i;
    notifyListeners();
  }

  // --- consultas ---
  Future<List<Map<String, dynamic>>> tareas() async => lista(await api.get('/api/v1/tareas/mias')).where((t) => alumnos.length < 2 || t['alumnoId'] == alumnoId).toList();

  Future<Map<String, dynamic>> horario() async {
    final m = matricula;
    if (m == null) return {'clases': <dynamic>[]};
    return Map<String, dynamic>.from(await api.get('/api/v1/planificacion/horarios/seccion/${m['seccionId']}'));
  }

  Future<Map<String, dynamic>?> asistencia() async {
    final todos = lista(await api.get('/api/v1/asistencia/mias'));
    for (final a in todos) {
      if (a['alumnoId'] == alumnoId) return a;
    }
    return null;
  }

  Future<Map<String, dynamic>?> libreta() async {
    final r = await api.get('/api/v1/calificaciones/mias');
    final l = r is List ? lista(r) : [Map<String, dynamic>.from(r as Map)];
    for (final x in l) {
      if (x['alumnoId'] == alumnoId) return x;
    }
    return l.isEmpty ? null : l.first;
  }

  Future<List<Map<String, dynamic>>> cuotas() async => lista(await api.get('/api/v1/pagos/mias/cuotas')).where((c) => alumnos.length < 2 || c['alumnoId'] == alumnoId).toList();
}
