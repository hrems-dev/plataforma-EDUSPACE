import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/api.dart';
import '../../core/config.dart';
import '../../core/formato.dart';
import '../../core/tipos.dart';
import '../../core/sesion.dart';
import '../../ui/tema.dart';
import '../../ui/material.dart';
import '../../ui/widgets.dart';
import '../docente/datos.dart';
import 'datos.dart';

const _verde = Color(0xFF059669), _rojo = Color(0xFFDC2626), _ambar = Color(0xFFD97706), _azul = Color(0xFF2563EB);

// ---------------------------------------------------------------- Inicio
class PaginaInicioFamilia extends StatelessWidget {
  final FamiliaDatos f;
  const PaginaInicioFamilia({super.key, required this.f});

  Future<Map<String, dynamic>> _datos() async {
    final r = await Future.wait<dynamic>([f.tareas(), f.horario(), f.asistencia().catchError((_) => null)]);
    return {'tareas': r[0], 'horario': r[1], 'asistencia': r[2]};
  }

  @override
  Widget build(BuildContext context) => Cargador<Map<String, dynamic>>(
        cargar: _datos,
        construir: (c, d, _) {
          final tareas = (d['tareas'] as List<Map<String, dynamic>>).where((t) => t['estadoEntrega'] == 'SIN_ENTREGAR' || t['estadoEntrega'] == 'DEVUELTA').toList()
            ..sort((a, b) => '${a['fechaEntrega']}'.compareTo('${b['fechaEntrega']}'));
          final dia = DateTime.now().weekday;
          final clases = lista((d['horario'] as Map)['clases']).where((x) => x['diaSemana'] == dia).toList()..sort((a, b) => '${a['horaInicio']}'.compareTo('${b['horaInicio']}'));
          final asis = d['asistencia'] as Map<String, dynamic>?;
          final pct = ((asis?['resumen'] as Map?)?['dias'] as Map?)?['porcentajeAsistencia'];
          return ListView(padding: const EdgeInsets.all(16), children: [
            Text('Hola, ${primerNombre(f.nombre)}', style: Theme.of(c).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
            Text('${nombreDia(dia)} ${DateTime.now().day}'),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: _Dato('Pendientes', '${tareas.length}', tareas.isEmpty ? _verde : _ambar)),
              const SizedBox(width: 10),
              Expanded(child: _Dato('Asistencia', pct == null ? '—' : '${(pct as num).toStringAsFixed(0)}%', pct == null || pct >= 85 ? _verde : _rojo)),
              const SizedBox(width: 10),
              Expanded(child: _Dato('Clases hoy', '${clases.length}', _azul)),
            ]),
            const Titulo('Clases de hoy'),
            if (clases.isEmpty) Card(child: Padding(padding: const EdgeInsets.all(16), child: Text(f.matricula == null ? 'Aún no hay una matrícula activa este año, por eso no se muestran clases.' : 'No hay clases hoy.'))),
            for (final x in clases)
              Card(child: ListTile(
                leading: Container(width: 6, height: 40, decoration: BoxDecoration(color: colorCurso((x['cursoId'] as num).toInt()), borderRadius: BorderRadius.circular(3))),
                title: Text('${x['cursoNombre']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text('${hora('${x['horaInicio']}')} – ${hora('${x['horaFin']}')}${x['aulaNombre'] != null ? ' · ${x['aulaNombre']}' : ''}'),
              )),
            const Titulo('Tareas por entregar'),
            if (tareas.isEmpty) const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Estás al día. ¡Buen trabajo!'))),
            for (final t in tareas.take(5))
              Card(child: ListTile(
                title: Text('${t['titulo']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text('Entrega ${fechaHoraCorta('${t['fechaEntrega']}')}'),
                trailing: t['vencida'] == true ? const Insignia('Vencida', _rojo) : null,
              )),
          ]);
        },
      );
}

class _Dato extends StatelessWidget {
  final String etiqueta, valor;
  final Color color;
  const _Dato(this.etiqueta, this.valor, this.color);
  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(children: [Text(valor, style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: color)), Text(etiqueta, style: const TextStyle(fontSize: 12.5))]),
        ),
      );
}

// ---------------------------------------------------------------- Tareas
class PaginaTareasFamilia extends StatefulWidget {
  final FamiliaDatos f;
  const PaginaTareasFamilia({super.key, required this.f});
  @override
  State<PaginaTareasFamilia> createState() => _PaginaTareasFamiliaState();
}

class _PaginaTareasFamiliaState extends State<PaginaTareasFamilia> {
  int _v = 0;

  (String, Color) _estado(String e) => switch (e) {
        'SIN_ENTREGAR' => ('Sin entregar', _ambar),
        'ENTREGADA' => ('Entregada', _azul),
        'CALIFICADA' => ('Calificada', _verde),
        'DEVUELTA' => ('Devuelta', _rojo),
        _ => (e, Colors.grey),
      };

  Future<void> _entregar(Map<String, dynamic> t) async {
    final ok = await showModalBottomSheet<bool>(context: context, isScrollControlled: true, showDragHandle: true, builder: (_) => _FormEntrega(tarea: t));
    if (ok == true) setState(() => _v++);
  }

  @override
  Widget build(BuildContext context) {
    final apoderado = context.read<Sesion>().perfil == Perfil.apoderado;
    return Cargador<List<Map<String, dynamic>>>(
      key: ValueKey(_v),
      cargar: widget.f.tareas,
      construir: (c, ts, _) => ts.isEmpty
          ? const Estado(icono: Icons.assignment_turned_in_outlined, texto: 'No hay tareas publicadas por ahora.')
          : ListView(padding: const EdgeInsets.all(16), children: [
              if (apoderado) const Padding(padding: EdgeInsets.only(bottom: 10), child: Text('Las entregas las hace el alumno desde su cuenta. Aquí ves qué tiene pendiente.', style: TextStyle(fontSize: 12.5))),
              for (final t in ts)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Card(
                    child: ListTile(
                      title: Text('${t['titulo']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                      subtitle: Text('Entrega: ${fechaHoraCorta('${t['fechaEntrega']}')}${t['vencida'] == true ? ' · vencida' : ''}'),
                      trailing: Insignia(_estado('${t['estadoEntrega']}').$1, _estado('${t['estadoEntrega']}').$2),
                      onTap: !apoderado && (t['estadoEntrega'] == 'SIN_ENTREGAR' || t['estadoEntrega'] == 'DEVUELTA') && (t['vencida'] != true || t['permiteTardia'] == true) ? () => _entregar(t) : null,
                    ),
                  ),
                ),
            ]),
    );
  }
}

class _FormEntrega extends StatefulWidget {
  final Map<String, dynamic> tarea;
  const _FormEntrega({required this.tarea});
  @override
  State<_FormEntrega> createState() => _FormEntregaState();
}

class _FormEntregaState extends State<_FormEntrega> {
  final _com = TextEditingController();
  List<PlatformFile> _archivos = [];
  bool _enviando = false;
  String? _error;

  Future<void> _elegir() async {
    final r = await FilePicker.platform.pickFiles(allowMultiple: true, withData: true, type: FileType.custom, allowedExtensions: extensionesMaterial);
    if (r != null) setState(() => _archivos = r.files.where((f) => f.bytes != null).toList());
  }

  Future<void> _enviar() async {
    if (_archivos.isEmpty && _com.text.trim().isEmpty) return setState(() => _error = 'Adjunta un archivo o escribe un comentario.');
    if (_archivos.any((a) => a.size > 10 * 1024 * 1024)) return setState(() => _error = 'Cada archivo puede pesar hasta 10 MB.');
    setState(() { _enviando = true; _error = null; });
    final api = context.read<Api>();
    try {
      final id = widget.tarea['tareaId'];
      final refs = <Map<String, dynamic>>[];
      for (final a in _archivos) {
        final tipo = tipoPorNombre(a.name);
        final u = await api.post('/api/v1/tareas/$id/entregas/archivo-url', cuerpo: {'nombreArchivo': a.name, 'contentType': tipo, 'tamanoBytes': a.size});
        await api.subir('${u['uploadUrl']}', a.bytes!, tipo);
        refs.add({'nombreArchivo': a.name, 'ruta': u['ruta'], 'contentType': tipo, 'tamanoBytes': a.size});
      }
      await api.post('/api/v1/tareas/$id/entregas', cuerpo: {'comentario': _com.text.trim().isEmpty ? null : _com.text.trim(), 'archivos': refs});
      if (mounted) {
        avisar(context, 'Tarea entregada.');
        Navigator.pop(context, true);
      }
    } on Object catch (e) {
      if (mounted) setState(() { _error = mensajeError(e); _enviando = false; });
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20),
        child: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('Entregar: ${widget.tarea['titulo']}', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            OutlinedButton.icon(onPressed: _enviando ? null : _elegir, icon: const Icon(Icons.attach_file), label: Text(_archivos.isEmpty ? 'Adjuntar archivos' : '${_archivos.length} archivo(s): ${_archivos.map((a) => a.name).join(', ')}', overflow: TextOverflow.ellipsis)),
            const SizedBox(height: 10),
            TextField(controller: _com, maxLines: 3, decoration: const InputDecoration(labelText: 'Comentario (opcional)')),
            if (widget.tarea['estadoEntrega'] == 'DEVUELTA') const Padding(padding: EdgeInsets.only(top: 8), child: Text('Tu entrega anterior se reemplaza por esta nueva versión.', style: TextStyle(fontSize: 12.5))),
            if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
            const SizedBox(height: 12),
            FilledButton(onPressed: _enviando ? null : _enviar, child: Text(_enviando ? 'Enviando…' : 'Enviar entrega')),
          ]),
        ),
      );
}

// ---------------------------------------------------------------- Horario
class PaginaHorarioFamilia extends StatelessWidget {
  final FamiliaDatos f;
  const PaginaHorarioFamilia({super.key, required this.f});
  @override
  Widget build(BuildContext context) => Cargador<Map<String, dynamic>>(
        cargar: f.horario,
        construir: (c, h, _) {
          final clases = lista(h['clases']);
          if (clases.isEmpty) return const Estado(icono: Icons.calendar_today, texto: 'El horario aún no está publicado.');
          final hoy = DateTime.now().weekday;
          return ListView(padding: const EdgeInsets.all(16), children: [
            for (var dia = 1; dia <= 7; dia++)
              if (clases.any((x) => x['diaSemana'] == dia)) ...[
                Titulo('${nombreDia(dia)}${dia == hoy ? ' · hoy' : ''}'),
                for (final x in (clases.where((x) => x['diaSemana'] == dia).toList()..sort((a, b) => '${a['horaInicio']}'.compareTo('${b['horaInicio']}'))))
                  Card(child: ListTile(
                    leading: Container(width: 6, height: 40, decoration: BoxDecoration(color: colorCurso((x['cursoId'] as num).toInt()), borderRadius: BorderRadius.circular(3))),
                    title: Text('${x['cursoNombre']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('${hora('${x['horaInicio']}')} – ${hora('${x['horaFin']}')} · ${x['docenteNombre']}${x['aulaNombre'] != null ? ' · ${x['aulaNombre']}' : ''}'),
                  )),
              ],
          ]);
        },
      );
}

// ---------------------------------------------------------------- Notas
class PaginaNotasFamilia extends StatelessWidget {
  final FamiliaDatos f;
  const PaginaNotasFamilia({super.key, required this.f});
  @override
  Widget build(BuildContext context) => Cargador<Map<String, dynamic>?>(
        cargar: () async {
          final l = await f.libreta();
          if (l == null) return null;
          // calificaciones-ms solo envía «Curso N»: el nombre real sale del horario de la sección.
          final nombres = {for (final x in lista((await f.horario())['clases'])) (x['cursoId'] as num).toInt(): '${x['cursoNombre']}'};
          return {...l, 'cursos': [for (final k in lista(l['cursos'])) {...k, 'cursoNombre': nombres[(k['cursoId'] as num).toInt()] ?? k['cursoNombre']}]};
        },
        construir: (c, l, _) {
          final cursos = lista(l?['cursos']);
          final boletines = lista(l?['boletines']);
          if (cursos.isEmpty && boletines.isEmpty) return const Estado(icono: Icons.grade_outlined, texto: 'Aún no hay notas registradas.');
          return ListView(padding: const EdgeInsets.all(16), children: [
            for (final x in cursos)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Card(
                  child: ListTile(
                    leading: Container(width: 6, height: 40, decoration: BoxDecoration(color: colorCurso((x['cursoId'] as num).toInt()), borderRadius: BorderRadius.circular(3))),
                    title: Text('${x['cursoNombre']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text([for (final e in (x['notasPorPeriodo'] as Map? ?? {}).entries) 'P${e.key}: ${e.value['literal']}'].join('  ·  ')),
                    trailing: x['promedioFinal'] == null ? null : Insignia('${(x['promedioFinal'] as num).toStringAsFixed(1)} ${x['literalFinal'] ?? ''}', _azul),
                  ),
                ),
              ),
            if (boletines.isNotEmpty) ...[
              const Titulo('Boletines'),
              for (final b in boletines)
                Card(child: ListTile(
                  leading: const Icon(Icons.picture_as_pdf_outlined),
                  title: Text('Boletín ${b['periodoNombre'] ?? b['periodoId'] ?? ''}'),
                  trailing: const Icon(Icons.download),
                  onTap: () async {
                    final url = '${b['urlDescarga'] ?? ''}';
                    if (url.isEmpty) return;
                    final ok = await launchUrl(Uri.parse(url.startsWith('http') ? url : '${Config.baseUrl}$url'), mode: LaunchMode.externalApplication);
                    if (!ok && context.mounted) avisar(context, 'No se pudo abrir el boletín.', error: true);
                  },
                )),
            ],
          ]);
        },
      );
}

// ---------------------------------------------------------------- Asistencia
class PaginaAsistenciaFamilia extends StatefulWidget {
  final FamiliaDatos f;
  final bool apoderado;
  const PaginaAsistenciaFamilia({super.key, required this.f, required this.apoderado});
  @override
  State<PaginaAsistenciaFamilia> createState() => _PaginaAsistenciaFamiliaState();
}

class _PaginaAsistenciaFamiliaState extends State<PaginaAsistenciaFamilia> {
  int _v = 0;

  Future<void> _justificar() async {
    final motivo = TextEditingController();
    var tipo = 'MEDICA';
    var desde = DateTime.now(), hasta = DateTime.now();
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: const Text('Justificar inasistencia'),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              DropdownButtonFormField<String>(initialValue: tipo, decoration: const InputDecoration(labelText: 'Motivo'), items: const [DropdownMenuItem(value: 'MEDICA', child: Text('Médica')), DropdownMenuItem(value: 'FAMILIAR', child: Text('Familiar')), DropdownMenuItem(value: 'OTRO', child: Text('Otro'))], onChanged: (v) => set(() => tipo = v ?? tipo)),
              const SizedBox(height: 10),
              OutlinedButton(onPressed: () async { final d = await showDatePicker(context: c, initialDate: desde, firstDate: DateTime.now().subtract(const Duration(days: 60)), lastDate: DateTime.now().add(const Duration(days: 30))); if (d != null) set(() { desde = d; if (hasta.isBefore(d)) hasta = d; }); }, child: Text('Desde: ${fechaIso(desde)}')),
              OutlinedButton(onPressed: () async { final d = await showDatePicker(context: c, initialDate: hasta, firstDate: desde, lastDate: DateTime.now().add(const Duration(days: 30))); if (d != null) set(() => hasta = d); }, child: Text('Hasta: ${fechaIso(hasta)}')),
              const SizedBox(height: 10),
              TextField(controller: motivo, maxLines: 3, decoration: const InputDecoration(labelText: 'Detalle')),
            ]),
          ),
          actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Enviar'))],
        ),
      ),
    );
    if (ok != true || !mounted) return;
    if (motivo.text.trim().length < 5) return avisar(context, 'Cuéntanos brevemente el motivo.', error: true);
    try {
      await context.read<Api>().post('/api/v1/asistencia/justificaciones', cuerpo: {'alumnoId': widget.f.alumnoId, 'fechaDesde': fechaIso(desde), 'fechaHasta': fechaIso(hasta), 'tipo': tipo, 'motivo': motivo.text.trim()});
      if (mounted) { avisar(context, 'Justificación enviada. El colegio la revisará.'); setState(() => _v++); }
    } on Object catch (e) {
      if (mounted) avisar(context, mensajeError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Cargador<Map<String, dynamic>?>(
        key: ValueKey(_v),
        cargar: widget.f.asistencia,
        construir: (c, a, _) {
          final dias = ((a?['resumen'] as Map?)?['dias'] as Map?) ?? {};
          final ult = lista(a?['ultimos']);
          const col = {'PRESENTE': _verde, 'FALTA': _rojo, 'TARDANZA': _ambar, 'JUSTIFICADO': _azul};
          return ListView(padding: const EdgeInsets.all(16), children: [
            Row(children: [
              Expanded(child: _Dato('Asistencia', dias['porcentajeAsistencia'] == null ? '—' : '${(dias['porcentajeAsistencia'] as num).toStringAsFixed(0)}%', _verde)),
              const SizedBox(width: 8),
              Expanded(child: _Dato('Faltas', '${dias['faltas'] ?? 0}', _rojo)),
              const SizedBox(width: 8),
              Expanded(child: _Dato('Tardanzas', '${dias['tardanzas'] ?? 0}', _ambar)),
            ]),
            if (widget.apoderado) Padding(padding: const EdgeInsets.only(top: 12), child: OutlinedButton.icon(onPressed: _justificar, icon: const Icon(Icons.edit_note), label: const Text('Justificar una inasistencia'))),
            const Titulo('Últimos registros'),
            if (ult.isEmpty) const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Aún no hay registros de asistencia.'))),
            for (final u in ult)
              Card(child: ListTile(dense: true, title: Text(fechaCorta('${u['fecha']}')), trailing: Insignia('${u['estado']}'.toLowerCase(), col['${u['estado']}'] ?? Colors.grey))),
          ]);
        },
      );
}

// ---------------------------------------------------------------- Exámenes
class PaginaExamenesFamilia extends StatelessWidget {
  final FamiliaDatos f;
  const PaginaExamenesFamilia({super.key, required this.f});
  @override
  Widget build(BuildContext context) => Cargador<List<Map<String, dynamic>>>(
        cargar: () async => lista(await context.read<Api>().get('/api/v1/examenes/disponibles')),
        construir: (c, es, _) => es.isEmpty
            ? const Estado(icono: Icons.quiz_outlined, texto: 'No tienes exámenes disponibles ahora.')
            : ListView(padding: const EdgeInsets.all(16), children: [
                const Padding(padding: EdgeInsets.only(bottom: 10), child: Text('Por ahora los exámenes se rinden desde la web de EduSpace; aquí ves cuándo abren.', style: TextStyle(fontSize: 12.5))),
                for (final e in es)
                  Card(child: ListTile(
                    title: Text('${e['titulo']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text('Hasta ${fechaHoraCorta('${e['fechaFin']}')} · ${e['duracionMin']} min · intentos ${e['intentosRealizados']}/${e['intentosMax']}'),
                    trailing: e['puedeRendir'] == true ? const Insignia('Disponible', _verde) : null,
                  )),
              ]),
      );
}

// ---------------------------------------------------------------- Pagos
class PaginaPagosFamilia extends StatefulWidget {
  final FamiliaDatos f;
  const PaginaPagosFamilia({super.key, required this.f});
  @override
  State<PaginaPagosFamilia> createState() => _PaginaPagosFamiliaState();
}

class _PaginaPagosFamiliaState extends State<PaginaPagosFamilia> {
  int _v = 0;

  Future<void> _pagar(Map<String, dynamic> cu) async {
    final saldo = (cu['saldo'] as num);
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Pago en línea (simulado)'),
        content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${cu['descripcion'] ?? cu['conceptoNombre']}'),
          const SizedBox(height: 6),
          Text(soles(saldo), style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 10),
          const Text('Es una pasarela de prueba: no se hace ningún cargo real.', style: TextStyle(fontSize: 12.5)),
        ]),
        actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Pagar')),],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await context.read<Api>().post('/api/v1/pagos/online', cuerpo: {
        'alumnoId': cu['alumnoId'], 'pagadorNombre': context.read<Sesion>().usuario, 'montoTotal': saldo, 'medio': 'ONLINE_SIMULADO',
        'idempotencyKey': 'movil-${cu['id']}-${DateTime.now().millisecondsSinceEpoch}',
        'detalles': [{'cuotaId': cu['id'], 'montoAplicado': saldo}],
      });
      if (mounted) { avisar(context, 'Pago registrado. ¡Gracias!'); setState(() => _v++); }
    } on Object catch (e) {
      if (mounted) avisar(context, mensajeError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Cargador<List<Map<String, dynamic>>>(
        key: ValueKey(_v),
        cargar: widget.f.cuotas,
        construir: (c, cs, _) {
          final deuda = cs.where((x) => (x['saldo'] as num) > 0 && x['estado'] != 'ANULADA').toList();
          final total = deuda.fold<num>(0, (a, x) => a + (x['saldo'] as num));
          if (cs.isEmpty) return const Estado(icono: Icons.payments_outlined, texto: 'No hay cuotas registradas.');
          return ListView(padding: const EdgeInsets.all(16), children: [
            Card(child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Saldo pendiente'), Text(soles(total), style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: total > 0 ? _ambar : _verde))])),
              Insignia(deuda.isEmpty ? 'Al día' : '${deuda.length} cuota(s)', deuda.isEmpty ? _verde : _ambar),
            ]))),
            const Titulo('Cuotas'),
            for (final x in cs)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Card(child: ListTile(
                  title: Text('${x['descripcion'] ?? x['conceptoNombre']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text('Vence ${fechaCorta('${x['fechaVencimiento']}')} · ${soles(x['saldo'] as num)}${(x['saldo'] as num) > 0 && (x['diasAtraso'] ?? 0) > 0 ? ' · ${x['diasAtraso']} días de atraso' : ''}'),
                  trailing: (x['saldo'] as num) > 0 ? FilledButton.tonal(onPressed: () => _pagar(x), child: const Text('Pagar')) : const Insignia('Pagada', _verde),
                )),
              ),
          ]);
        },
      );
}

// ---------------------------------------------------------------- Materiales
/// Materiales que suben los docentes, agrupados por curso (los cursos salen del horario de la sección del alumno).
class PaginaMaterialesFamilia extends StatelessWidget {
  final FamiliaDatos f;
  const PaginaMaterialesFamilia({super.key, required this.f});

  Future<List<({int id, String nombre, List<Map<String, dynamic>> materiales})>> _cargar(BuildContext context) async {
    final m = f.matricula;
    if (m == null) return [];
    final api = context.read<Api>();
    final h = await f.horario();
    final cursos = <int, String>{};
    for (final c in lista(h['clases'])) {
      cursos[(c['cursoId'] as num).toInt()] = '${c['cursoNombre']}';
    }
    final seccionId = (m['seccionId'] as num).toInt();
    final r = await Future.wait(cursos.entries.map((e) async => (
          id: e.key,
          nombre: e.value,
          materiales: lista(await api.get('/api/v1/tareas/materiales', query: {'seccionId': seccionId, 'cursoId': e.key}))
        )));
    return r.where((x) => x.materiales.isNotEmpty).toList()..sort((a, b) => a.nombre.compareTo(b.nombre));
  }

  @override
  Widget build(BuildContext context) => Cargador<List<({int id, String nombre, List<Map<String, dynamic>> materiales})>>(
        cargar: () => _cargar(context),
        construir: (c, grupos, _) => grupos.isEmpty
            ? const Estado(icono: Icons.folder_open, texto: 'Tus docentes todavía no han subido materiales.')
            : ListView(padding: const EdgeInsets.all(16), children: [
                for (final g in grupos) ...[
                  Titulo(g.nombre),
                  for (final m in g.materiales) Padding(padding: const EdgeInsets.only(bottom: 8), child: FilaMaterial(m: m)),
                ],
              ]),
      );
}
