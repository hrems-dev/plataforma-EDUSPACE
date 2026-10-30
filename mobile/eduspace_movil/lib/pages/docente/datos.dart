import '../../core/api.dart';

List<Map<String, dynamic>> lista(dynamic r) {
  final l = r is Map ? (r['contenido'] ?? r['content'] ?? const []) : r;
  return List<Map<String, dynamic>>.from((l as List? ?? const []).map((e) => Map<String, dynamic>.from(e as Map)));
}

/// Acceso a los datos del docente: quién es, qué enseña y cuándo.
class DocenteDatos {
  final Api api;
  DocenteDatos(this.api);
  int? _id;
  String nombre = '';

  Future<int> docenteId() async {
    if (_id != null) return _id!;
    final r = await api.get('/api/v1/planificacion/docentes/mi');
    _id = (r['docenteId'] as num).toInt();
    nombre = '${r['nombre'] ?? ''}';
    return _id!;
  }

  Future<List<Map<String, dynamic>>> clases() async {
    final id = await docenteId();
    final r = await api.get('/api/v1/planificacion/horarios/docente/$id');
    return lista(r is Map ? r['clases'] : r)..sort((a, b) {
      final d = ((a['diaSemana'] as num).compareTo(b['diaSemana'] as num));
      return d != 0 ? d : '${a['horaInicio']}'.compareTo('${b['horaInicio']}');
    });
  }

  Future<List<Map<String, dynamic>>> cursos() async {
    final id = await docenteId();
    final anio = await api.get('/api/v1/planificacion/anios/activo');
    final todas = lista(await api.get('/api/v1/planificacion/asignaciones', query: {'anioId': anio['id']}));
    // Con `anioId` el servicio lista las del año completo: se queda con las de este docente.
    return todas.where((a) => (a['docenteId'] as num?)?.toInt() == id).toList();
  }

  Future<int> periodoVigente() async {
    final anio = await api.get('/api/v1/planificacion/anios/activo');
    final ps = lista(await api.get('/api/v1/planificacion/periodos', query: {'anioId': anio['id']}));
    final hoy = DateTime.now().toIso8601String().substring(0, 10);
    final p = ps.firstWhere((p) => '${p['fechaInicio']}'.compareTo(hoy) <= 0 && '${p['fechaFin']}'.compareTo(hoy) >= 0, orElse: () => ps.isNotEmpty ? ps.first : {});
    if (p['id'] == null) throw ApiException(404, 'No hay un periodo académico configurado.');
    return (p['id'] as num).toInt();
  }
}
