import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/api.dart';
import '../core/formato.dart';
import '../core/tipos.dart';
import 'widgets.dart';

/// Un material de curso (archivo o enlace). Tocar lo abre; con `alEliminar` se muestra el botón de borrar (docente).
class FilaMaterial extends StatelessWidget {
  final Map<String, dynamic> m;
  final Future<void> Function()? alEliminar;
  const FilaMaterial({super.key, required this.m, this.alEliminar});

  bool get _enlace => m['tipo'] == 'ENLACE';

  IconData get _icono {
    if (_enlace) return Icons.link;
    final t = '${m['contentType'] ?? ''}';
    if (t.contains('pdf')) return Icons.picture_as_pdf_outlined;
    if (t.startsWith('image/')) return Icons.image_outlined;
    if (t.contains('presentation')) return Icons.slideshow_outlined;
    if (t.contains('spreadsheet')) return Icons.table_chart_outlined;
    if (t.contains('zip')) return Icons.folder_zip_outlined;
    return Icons.description_outlined;
  }

  Future<void> _abrir(BuildContext context) async {
    try {
      String url;
      if (_enlace) {
        url = '${m['enlace']}';
      } else {
        final d = await context.read<Api>().get('/api/v1/tareas/materiales/${m['id']}/url');
        url = '${d['urlDescarga']}';
      }
      final ok = await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
      if (!ok && context.mounted) avisar(context, 'No se pudo abrir el material.', error: true);
    } on Object catch (e) {
      if (context.mounted) avisar(context, mensajeError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detalle = _enlace ? '${m['enlace']}'.replaceFirst(RegExp(r'^https?://'), '') : '${m['nombreArchivo']} · ${tamanoLegible((m['tamanoBytes'] as num?) ?? 0)}';
    return Card(
      child: ListTile(
        leading: CircleAvatar(backgroundColor: Theme.of(context).colorScheme.primaryContainer, child: Icon(_icono)),
        title: Text('${m['titulo']}', style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text('${(m['descripcion'] ?? '') == '' ? '' : '${m['descripcion']}\n'}$detalle · ${fechaCorta('${m['creadoEn']}')}', maxLines: 4, overflow: TextOverflow.ellipsis),
        isThreeLine: (m['descripcion'] ?? '') != '',
        trailing: alEliminar == null
            ? const Icon(Icons.open_in_new)
            : IconButton(
                tooltip: 'Eliminar',
                icon: const Icon(Icons.delete_outline),
                onPressed: () async {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (c) => AlertDialog(
                      title: const Text('Eliminar material'),
                      content: Text('«${m['titulo']}» dejará de verse para los alumnos.'),
                      actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Eliminar'))],
                    ),
                  );
                  if (ok == true) await alEliminar!();
                },
              ),
        onTap: () => _abrir(context),
      ),
    );
  }
}
