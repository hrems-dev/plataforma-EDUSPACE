import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api.dart';
import '../../core/formato.dart';
import '../../ui/widgets.dart';
import 'datos.dart';

const _estadoTarea = {'BORRADOR': ('Borrador', Color(0xFF6B7280)), 'PUBLICADA': ('Publicada', Color(0xFF059669)), 'CERRADA': ('Cerrada', Color(0xFFDC2626))};

class TareasDocente extends StatefulWidget {
  final DocenteDatos d;
  final int seccionId, cursoId;
  const TareasDocente({super.key, required this.d, required this.seccionId, required this.cursoId});
  @override
  State<TareasDocente> createState() => _TareasDocenteState();
}

class _TareasDocenteState extends State<TareasDocente> {
  int _v = 0;

  Future<List<Map<String, dynamic>>> _cargar() async => lista(await context.read<Api>().get('/api/v1/tareas', query: {'seccionId': widget.seccionId, 'cursoId': widget.cursoId, 'size': 50}));

  Future<void> _nueva() async {
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _FormTarea(d: widget.d, seccionId: widget.seccionId, cursoId: widget.cursoId),
    );
    if (ok == true) setState(() => _v++);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.transparent,
        floatingActionButton: FloatingActionButton.extended(onPressed: _nueva, icon: const Icon(Icons.add), label: const Text('Nueva tarea')),
        body: Cargador<List<Map<String, dynamic>>>(
          key: ValueKey(_v),
          cargar: _cargar,
          construir: (c, ts, _) => ts.isEmpty
              ? const Estado(icono: Icons.assignment_outlined, texto: 'Aún no hay tareas en este curso. Crea la primera con «Nueva tarea».')
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
                  itemCount: ts.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (c, i) {
                    final t = ts[i];
                    final e = _estadoTarea['${t['estado']}'] ?? ('${t['estado']}', Colors.grey);
                    return Card(
                      child: ListTile(
                        title: Text('${t['titulo']}', style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: Text('Entrega: ${fechaHoraCorta('${t['fechaEntrega']}')}\nEntregadas ${t['entregadas'] ?? 0} · Por calificar ${((t['entregadas'] ?? 0) as num) - ((t['calificadas'] ?? 0) as num)}'),
                        isThreeLine: true,
                        trailing: Insignia(e.$1, e.$2),
                        onTap: () async {
                          await Navigator.push(context, MaterialPageRoute(builder: (_) => PaginaTareaDocente(tarea: t)));
                          if (mounted) setState(() => _v++);
                        },
                      ),
                    );
                  },
                ),
        ),
      );
}

class _FormTarea extends StatefulWidget {
  final DocenteDatos d;
  final int seccionId, cursoId;
  const _FormTarea({required this.d, required this.seccionId, required this.cursoId});
  @override
  State<_FormTarea> createState() => _FormTareaState();
}

class _FormTareaState extends State<_FormTarea> {
  final _t = TextEditingController(), _d = TextEditingController(), _p = TextEditingController(text: '20');
  DateTime _entrega = DateTime.now().add(const Duration(days: 7, hours: 1));
  bool _tardia = false, _publicar = true, _enviando = false;
  String? _error;

  Future<void> _elegirFecha() async {
    final f = await showDatePicker(context: context, initialDate: _entrega, firstDate: DateTime.now(), lastDate: DateTime.now().add(const Duration(days: 365)));
    if (f == null || !mounted) return;
    final h = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_entrega));
    setState(() => _entrega = DateTime(f.year, f.month, f.day, h?.hour ?? 23, h?.minute ?? 59));
  }

  Future<void> _guardar() async {
    if (_t.text.trim().length < 3) return setState(() => _error = 'Escribe un título.');
    final pts = num.tryParse(_p.text.replaceAll(',', '.'));
    if (pts == null || pts <= 0) return setState(() => _error = 'El puntaje máximo debe ser un número mayor que 0.');
    setState(() { _enviando = true; _error = null; });
    final api = context.read<Api>();
    try {
      final periodo = await widget.d.periodoVigente();
      final r = await api.post('/api/v1/tareas', cuerpo: {
        'periodoId': periodo, 'seccionId': widget.seccionId, 'cursoId': widget.cursoId, 'titulo': _t.text.trim(), 'descripcion': _d.text.trim(),
        'fechaEntrega': _entrega.toIso8601String().substring(0, 19), 'permiteTardia': _tardia, 'puntajeMaximo': pts,
      });
      if (_publicar) await api.patch('/api/v1/tareas/${r['id']}/publicar');
      if (mounted) Navigator.pop(context, true);
    } on Object catch (e) {
      if (mounted) setState(() { _error = mensajeError(e); _enviando = false; });
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20),
        child: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('Nueva tarea', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            TextField(controller: _t, decoration: const InputDecoration(labelText: 'Título')),
            const SizedBox(height: 10),
            TextField(controller: _d, maxLines: 3, decoration: const InputDecoration(labelText: 'Instrucciones (opcional)')),
            const SizedBox(height: 10),
            OutlinedButton.icon(onPressed: _elegirFecha, icon: const Icon(Icons.event), label: Text('Entrega: ${fechaHoraCorta(_entrega.toIso8601String())}')),
            const SizedBox(height: 10),
            TextField(controller: _p, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Puntaje máximo')),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Aceptar entregas tardías'), value: _tardia, onChanged: (v) => setState(() => _tardia = v)),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Publicar ahora'), subtitle: const Text('Los alumnos y apoderados reciben el aviso.'), value: _publicar, onChanged: (v) => setState(() => _publicar = v)),
            if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
            FilledButton(onPressed: _enviando ? null : _guardar, child: Text(_enviando ? 'Guardando…' : 'Crear tarea')),
          ]),
        ),
      );
}

/// Detalle de una tarea: publicar/cerrar y calificar entregas.
class PaginaTareaDocente extends StatefulWidget {
  final Map<String, dynamic> tarea;
  const PaginaTareaDocente({super.key, required this.tarea});
  @override
  State<PaginaTareaDocente> createState() => _PaginaTareaDocenteState();
}

class _PaginaTareaDocenteState extends State<PaginaTareaDocente> {
  int _v = 0;

  Future<void> _cambiar(String accion) async {
    try {
      await context.read<Api>().patch('/api/v1/tareas/${widget.tarea['id']}/$accion');
      setState(() => widget.tarea['estado'] = accion == 'publicar' ? 'PUBLICADA' : 'CERRADA');
      if (mounted) avisar(context, accion == 'publicar' ? 'Tarea publicada.' : 'Tarea cerrada.');
    } on Object catch (e) {
      if (mounted) avisar(context, mensajeError(e), error: true);
    }
  }

  Future<void> _calificar(Map<String, dynamic> fila) async {
    final e = Map<String, dynamic>.from(fila['entrega'] as Map);
    e['alumnoNombre'] = fila['alumnoNombre'];
    final nota = TextEditingController(text: e['nota']?.toString() ?? '');
    final retro = TextEditingController(text: '${e['retroalimentacion'] ?? ''}');
    final max = widget.tarea['puntajeMaximo'];
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(nombreNatural('${e['alumnoNombre']}')),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          if ('${e['comentario'] ?? ''}'.isNotEmpty) Align(alignment: Alignment.centerLeft, child: Text('«${e['comentario']}»')),
          Align(alignment: Alignment.centerLeft, child: Text('${(e['archivos'] as List?)?.length ?? 0} archivo(s) entregado(s)${e['esTardia'] == true ? ' · tardía' : ''}', style: const TextStyle(fontSize: 12.5))),
          const SizedBox(height: 10),
          TextField(controller: nota, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: 'Nota (máx. $max)')),
          const SizedBox(height: 10),
          TextField(controller: retro, maxLines: 3, decoration: const InputDecoration(labelText: 'Retroalimentación')),
        ]),
        actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Calificar'))],
      ),
    );
    if (ok != true || !mounted) return;
    final n = num.tryParse(nota.text.replaceAll(',', '.'));
    if (n == null) return avisar(context, 'Escribe una nota válida.', error: true);
    try {
      await context.read<Api>().patch('/api/v1/tareas/entregas/${e['id']}/calificar', cuerpo: {'nota': n, 'retroalimentacion': retro.text.trim()});
      setState(() => _v++);
    } on Object catch (x) {
      if (mounted) avisar(context, mensajeError(x), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.tarea;
    final est = '${t['estado']}';
    return Scaffold(
      appBar: AppBar(title: Text('${t['titulo']}', overflow: TextOverflow.ellipsis)),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            Expanded(child: Text('Entrega: ${fechaHoraCorta('${t['fechaEntrega']}')} · ${t['puntajeMaximo']} pts')),
            if (est == 'BORRADOR') FilledButton(onPressed: () => _cambiar('publicar'), child: const Text('Publicar')),
            if (est == 'PUBLICADA') OutlinedButton(onPressed: () => _cambiar('cerrar'), child: const Text('Cerrar')),
          ]),
        ),
        Expanded(
          child: est == 'BORRADOR'
              ? const Estado(icono: Icons.edit_note, texto: 'La tarea es un borrador. Publícala para recibir entregas.')
              : Cargador<List<Map<String, dynamic>>>(
                  key: ValueKey(_v),
                  cargar: () async => lista(await context.read<Api>().get('/api/v1/tareas/${t['id']}/entregas')),
                  construir: (c, es, _) => es.isEmpty
                      ? const Estado(icono: Icons.inbox_outlined, texto: 'Todavía nadie entrega esta tarea.')
                      : ListView.separated(
                          padding: const EdgeInsets.all(16),
                          itemCount: es.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (c, i) {
                            final fila = es[i];
                            final ent = fila['entrega'] as Map?;
                            final estado = '${fila['estado']}';
                            final sin = ent == null || estado == 'SIN_ENTREGAR';
                            return Card(
                              child: ListTile(
                                title: Text(nombreNatural('${fila['alumnoNombre']}'), style: const TextStyle(fontWeight: FontWeight.w600)),
                                subtitle: Text(sin ? 'Aún no entrega' : 'Entregó ${hace('${ent['entregadaEn']}')}${ent['esTardia'] == true ? ' · tardía' : ''}'),
                                trailing: sin
                                    ? const Insignia('Sin entregar', Color(0xFF6B7280))
                                    : estado == 'CALIFICADA'
                                        ? Insignia('${ent['nota']}/${t['puntajeMaximo']}', const Color(0xFF059669))
                                        : const Insignia('Por calificar', Color(0xFFD97706)),
                                onTap: sin ? null : () => _calificar(fila),
                              ),
                            );
                          },
                        ),
                ),
        ),
      ]),
    );
  }
}
