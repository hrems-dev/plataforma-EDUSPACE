import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api.dart';
import '../../core/tipos.dart';
import '../../ui/material.dart';
import '../../ui/widgets.dart';
import 'datos.dart';

/// Materiales de un curso: el docente sube archivos o enlaces y los elimina.
class MaterialesDocente extends StatefulWidget {
  final int seccionId, cursoId;
  const MaterialesDocente({super.key, required this.seccionId, required this.cursoId});
  @override
  State<MaterialesDocente> createState() => _MaterialesDocenteState();
}

class _MaterialesDocenteState extends State<MaterialesDocente> {
  int _v = 0;

  Future<List<Map<String, dynamic>>> _cargar() async => lista(await context.read<Api>().get('/api/v1/tareas/materiales', query: {'seccionId': widget.seccionId, 'cursoId': widget.cursoId}));

  Future<void> _subir() async {
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _FormMaterial(seccionId: widget.seccionId, cursoId: widget.cursoId),
    );
    if (ok == true) setState(() => _v++);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.transparent,
        floatingActionButton: FloatingActionButton.extended(onPressed: _subir, icon: const Icon(Icons.upload_file), label: const Text('Subir material')),
        body: Cargador<List<Map<String, dynamic>>>(
          key: ValueKey(_v),
          cargar: _cargar,
          construir: (c, ms, _) => ms.isEmpty
              ? const Estado(icono: Icons.folder_open, texto: 'Aún no hay materiales en este curso. Sube el primero con «Subir material».')
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
                  itemCount: ms.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (c, i) => FilaMaterial(
                    m: ms[i],
                    alEliminar: () async {
                      final api = this.context.read<Api>();
                      try {
                        await api.delete('/api/v1/tareas/materiales/${ms[i]['id']}');
                        if (mounted) { avisar(this.context, 'Material eliminado.'); setState(() => _v++); }
                      } on Object catch (e) {
                        if (mounted) avisar(this.context, mensajeError(e), error: true);
                      }
                    },
                  ),
                ),
        ),
      );
}

class _FormMaterial extends StatefulWidget {
  final int seccionId, cursoId;
  const _FormMaterial({required this.seccionId, required this.cursoId});
  @override
  State<_FormMaterial> createState() => _FormMaterialState();
}

class _FormMaterialState extends State<_FormMaterial> {
  final _t = TextEditingController(), _d = TextEditingController(), _e = TextEditingController();
  bool _archivo = true, _enviando = false;
  PlatformFile? _elegido;
  String? _error;

  Future<void> _elegir() async {
    final r = await FilePicker.platform.pickFiles(withData: true, type: FileType.custom, allowedExtensions: extensionesMaterial);
    final f = r?.files.firstOrNull;
    if (f == null) return;
    if (f.size > 10 * 1024 * 1024) return setState(() => _error = 'El archivo supera los 10 MB.');
    setState(() { _elegido = f; _error = null; });
  }

  Future<void> _publicar() async {
    if (_t.text.trim().length < 3) return setState(() => _error = 'Escribe un título.');
    if (_archivo && _elegido?.bytes == null) return setState(() => _error = 'Elige el archivo que vas a subir.');
    if (!_archivo && !RegExp(r'^https?://\S{4,}', caseSensitive: false).hasMatch(_e.text.trim())) return setState(() => _error = 'El enlace debe empezar con http:// o https://');
    setState(() { _enviando = true; _error = null; });
    final api = context.read<Api>();
    try {
      final base = {'seccionId': widget.seccionId, 'cursoId': widget.cursoId, 'titulo': _t.text.trim(), 'descripcion': _d.text.trim().isEmpty ? null : _d.text.trim()};
      if (_archivo) {
        final f = _elegido!;
        final tipo = tipoPorNombre(f.name);
        final u = await api.post('/api/v1/tareas/materiales/archivo-url', cuerpo: {'seccionId': widget.seccionId, 'cursoId': widget.cursoId, 'nombreArchivo': f.name, 'contentType': tipo, 'tamanoBytes': f.size});
        await api.subir('${u['uploadUrl']}', f.bytes!, tipo);
        await api.post('/api/v1/tareas/materiales', cuerpo: {...base, 'tipo': 'ARCHIVO', 'archivo': {'nombreArchivo': f.name, 'ruta': u['ruta'], 'contentType': tipo, 'tamanoBytes': f.size}});
      } else {
        await api.post('/api/v1/tareas/materiales', cuerpo: {...base, 'tipo': 'ENLACE', 'enlace': _e.text.trim()});
      }
      if (mounted) {
        avisar(context, 'Material publicado. La familia recibe un aviso.');
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
            Text('Subir material', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            SegmentedButton<bool>(
              segments: const [ButtonSegment(value: true, label: Text('Archivo'), icon: Icon(Icons.attach_file)), ButtonSegment(value: false, label: Text('Enlace'), icon: Icon(Icons.link))],
              selected: {_archivo},
              onSelectionChanged: (s) => setState(() => _archivo = s.first),
            ),
            const SizedBox(height: 12),
            TextField(controller: _t, decoration: const InputDecoration(labelText: 'Título')),
            const SizedBox(height: 10),
            TextField(controller: _d, maxLines: 2, decoration: const InputDecoration(labelText: 'Descripción (opcional)')),
            const SizedBox(height: 10),
            if (_archivo)
              OutlinedButton.icon(onPressed: _enviando ? null : _elegir, icon: const Icon(Icons.folder_open), label: Text(_elegido == null ? 'Elegir archivo (máx. 10 MB)' : _elegido!.name, overflow: TextOverflow.ellipsis))
            else
              TextField(controller: _e, keyboardType: TextInputType.url, autocorrect: false, decoration: const InputDecoration(labelText: 'Enlace', hintText: 'https://…')),
            if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
            const SizedBox(height: 14),
            FilledButton(onPressed: _enviando ? null : _publicar, child: Text(_enviando ? 'Publicando…' : 'Publicar material')),
          ]),
        ),
      );
}
