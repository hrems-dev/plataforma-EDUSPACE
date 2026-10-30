import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api.dart';
import '../../core/formato.dart';
import '../../ui/widgets.dart';

/// Acceso rápido desde el curso: abre el pase de lista de hoy.
class ResumenAsistencia extends StatelessWidget {
  final int seccionId;
  final String seccion, curso;
  const ResumenAsistencia({super.key, required this.seccionId, required this.seccion, required this.curso});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(20), children: [
        const Icon(Icons.fact_check_outlined, size: 48),
        const SizedBox(height: 10),
        Text('Pasar lista de $seccion', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 6),
        const Text('Se abre la asistencia del día. Marca a cada alumno (Presente, Tarde, Falta o Justificado) y guarda; al terminar puedes cerrar el registro.', textAlign: TextAlign.center),
        const SizedBox(height: 18),
        FilledButton.icon(
          icon: const Icon(Icons.play_arrow),
          label: const Text('Pasar lista de hoy'),
          onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PaginaAsistencia(seccionId: seccionId, seccion: seccion, curso: curso))),
        ),
      ]);
}

class PaginaAsistencia extends StatefulWidget {
  final int seccionId;
  final String seccion, curso;
  const PaginaAsistencia({super.key, required this.seccionId, required this.seccion, required this.curso});
  @override
  State<PaginaAsistencia> createState() => _PaginaAsistenciaState();
}

const _estados = {'PRESENTE': ('P', Color(0xFF059669)), 'TARDANZA': ('T', Color(0xFFD97706)), 'FALTA': ('F', Color(0xFFDC2626)), 'JUSTIFICADO': ('J', Color(0xFF2563EB))};

class _PaginaAsistenciaState extends State<PaginaAsistencia> {
  Map<String, dynamic>? _sesion;
  List<Map<String, dynamic>> _alumnos = [];
  Object? _error;
  bool _cargando = true, _guardando = false;

  bool get _cerrada => _sesion?['estado'] == 'CERRADA';

  @override
  void initState() {
    super.initState();
    _abrir();
  }

  Future<void> _abrir() async {
    setState(() { _cargando = true; _error = null; });
    try {
      final r = await context.read<Api>().post('/api/v1/asistencia/sesiones', cuerpo: {'seccionId': widget.seccionId, 'fecha': fechaIso(DateTime.now()), 'tipo': 'DIA'});
      _sesion = Map<String, dynamic>.from(r as Map);
      _alumnos = List<Map<String, dynamic>>.from((_sesion!['alumnos'] as List).map((e) => Map<String, dynamic>.from(e as Map)));
    } on Object catch (e) {
      _error = e;
    }
    if (mounted) setState(() => _cargando = false);
  }

  Future<void> _guardar({bool cerrar = false}) async {
    setState(() => _guardando = true);
    final api = context.read<Api>();
    try {
      final id = _sesion!['id'];
      await api.put('/api/v1/asistencia/sesiones/$id/registro', cuerpo: {
        'items': [for (final a in _alumnos) {'matriculaId': a['matriculaId'], 'estado': a['estado'], if (a['estado'] == 'TARDANZA') 'minutosTarde': a['minutosTarde'] ?? 5}]
      });
      if (cerrar) {
        await api.patch('/api/v1/asistencia/sesiones/$id/cerrar');
        _sesion!['estado'] = 'CERRADA';
      }
      if (mounted) avisar(context, cerrar ? 'Asistencia guardada y cerrada.' : 'Asistencia guardada.');
    } on Object catch (e) {
      if (mounted) avisar(context, mensajeError(e), error: true);
    }
    if (mounted) setState(() => _guardando = false);
  }

  void _todosPresentes() => setState(() {
        for (final a in _alumnos) {
          a['estado'] = 'PRESENTE';
        }
      });

  @override
  Widget build(BuildContext context) {
    final resumen = {for (final k in _estados.keys) k: _alumnos.where((a) => a['estado'] == k).length};
    return Scaffold(
      appBar: AppBar(title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Pasar lista', style: TextStyle(fontSize: 18)), Text('${widget.seccion} · hoy', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w400))])),
      body: _cargando
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Estado(icono: Icons.error_outline, texto: mensajeError(_error!), accion: 'Reintentar', alAccion: _abrir)
              : Column(children: [
                  if (_cerrada) Container(width: double.infinity, color: const Color(0xFFE8F5EE), padding: const EdgeInsets.all(10), child: const Text('Este registro ya está cerrado.', textAlign: TextAlign.center)),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
                    child: Row(children: [
                      Expanded(child: Wrap(spacing: 6, children: [for (final e in _estados.entries) Insignia('${e.value.$1} ${resumen[e.key]}', e.value.$2)])),
                      if (!_cerrada && _alumnos.isNotEmpty) TextButton(onPressed: _todosPresentes, child: const Text('Todos presentes')),
                    ]),
                  ),
                  Expanded(
                    child: _alumnos.isEmpty
                        ? const Estado(icono: Icons.group_off, texto: 'La sección no tiene alumnos matriculados.')
                        : ListView.separated(
                            padding: const EdgeInsets.all(12),
                            itemCount: _alumnos.length,
                            separatorBuilder: (_, _) => const SizedBox(height: 6),
                            itemBuilder: (c, i) {
                              final a = _alumnos[i];
                              return Card(
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                  child: Row(children: [
                                    Expanded(child: Text(nombreNatural('${a['alumnoNombre']}'), style: const TextStyle(fontWeight: FontWeight.w600))),
                                    for (final e in _estados.entries)
                                      Padding(
                                        padding: const EdgeInsets.only(left: 4),
                                        child: Semantics(
                                          button: true,
                                          label: '${e.key} ${nombreNatural('${a['alumnoNombre']}')}',
                                          child: InkResponse(
                                            onTap: _cerrada ? null : () => setState(() => a['estado'] = e.key),
                                            child: CircleAvatar(
                                              radius: 17,
                                              backgroundColor: a['estado'] == e.key ? e.value.$2 : e.value.$2.withValues(alpha: .1),
                                              child: Text(e.value.$1, style: TextStyle(color: a['estado'] == e.key ? Colors.white : e.value.$2, fontWeight: FontWeight.w700)),
                                            ),
                                          ),
                                        ),
                                      ),
                                  ]),
                                ),
                              );
                            },
                          ),
                  ),
                  if (!_cerrada && _alumnos.isNotEmpty)
                    SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(children: [
                          Expanded(child: OutlinedButton(onPressed: _guardando ? null : () => _guardar(), child: const Text('Guardar'))),
                          const SizedBox(width: 10),
                          Expanded(child: FilledButton(onPressed: _guardando ? null : () => _guardar(cerrar: true), child: const Text('Guardar y cerrar'))),
                        ]),
                      ),
                    ),
                ]),
    );
  }
}
