import 'package:flutter/material.dart';

import '../core/api.dart';

/// Carga datos y los muestra; ofrece «Reintentar» y recarga al deslizar hacia abajo.
class Cargador<T> extends StatefulWidget {
  final Future<T> Function() cargar;
  final Widget Function(BuildContext, T, Future<void> Function() recargar) construir;
  const Cargador({super.key, required this.cargar, required this.construir});
  @override
  State<Cargador<T>> createState() => _CargadorState<T>();
}

class _CargadorState<T> extends State<Cargador<T>> {
  late Future<T> _f;
  @override
  void initState() {
    super.initState();
    _f = widget.cargar();
  }

  Future<void> _recargar() async {
    final nuevo = widget.cargar();
    setState(() => _f = nuevo);
    try {
      await nuevo;
    } on Object {
      // el error se muestra en la propia pantalla
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<T>(
        future: _f,
        builder: (c, s) {
          if (s.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          if (s.hasError) {
            final e = s.error;
            return Estado(icono: e is ServicioCaido ? Icons.cloud_off : Icons.error_outline, texto: e is ApiException ? e.mensaje : 'No se pudo cargar.', accion: 'Reintentar', alAccion: _recargar);
          }
          return RefreshIndicator(onRefresh: _recargar, child: widget.construir(c, s.data as T, _recargar));
        },
      );
}

class Estado extends StatelessWidget {
  final IconData icono;
  final String texto;
  final String? accion;
  final VoidCallback? alAccion;
  const Estado({super.key, required this.icono, required this.texto, this.accion, this.alAccion});
  @override
  Widget build(BuildContext context) => ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.all(32), children: [
        const SizedBox(height: 60),
        Icon(icono, size: 48, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 12),
        Text(texto, textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyLarge),
        if (accion != null) ...[const SizedBox(height: 16), Center(child: FilledButton.tonal(onPressed: alAccion, child: Text(accion!)))],
      ]);
}

void avisar(BuildContext c, String texto, {bool error = false}) {
  ScaffoldMessenger.of(c)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(texto), backgroundColor: error ? Theme.of(c).colorScheme.error : null, behavior: SnackBarBehavior.floating));
}

String mensajeError(Object e) => e is ApiException ? e.mensaje : 'Ocurrió un error inesperado.';

class Insignia extends StatelessWidget {
  final String texto;
  final Color color;
  const Insignia(this.texto, this.color, {super.key});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
        decoration: BoxDecoration(color: color.withValues(alpha: .13), borderRadius: BorderRadius.circular(20)),
        child: Text(texto, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      );
}

class Titulo extends StatelessWidget {
  final String texto;
  const Titulo(this.texto, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
        child: Text(texto, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
      );
}

/// Tarjeta de curso con el color del curso.
class TarjetaCurso extends StatelessWidget {
  final String titulo, subtitulo;
  final Color color;
  final VoidCallback onTap;
  final Widget? extra;
  const TarjetaCurso({super.key, required this.titulo, required this.subtitulo, required this.color, required this.onTap, this.extra});
  @override
  Widget build(BuildContext context) => Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Row(children: [
            Container(width: 8, height: 84, color: color),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(titulo, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                  const SizedBox(height: 2),
                  Text(subtitulo, style: TextStyle(color: Theme.of(context).colorScheme.outline)),
                  if (extra != null) ...[const SizedBox(height: 6), extra!],
                ]),
              ),
            ),
            const Padding(padding: EdgeInsets.only(right: 10), child: Icon(Icons.chevron_right)),
          ]),
        ),
      );
}

/// Banner que aparece cuando el sistema no responde.
class AvisoServicio extends StatelessWidget {
  const AvisoServicio({super.key});
  @override
  Widget build(BuildContext context) => Material(
        color: const Color(0xFFFFF4E0),
        child: SafeArea(
          bottom: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            child: Row(children: const [
              Icon(Icons.cloud_off, size: 18, color: Color(0xFF9A5B00)),
              SizedBox(width: 8),
              Expanded(child: Text('El sistema no responde por ahora (puede estar reiniciándose). Reintenta en un momento.', style: TextStyle(fontSize: 12.5, color: Color(0xFF6B3F00)))),
            ]),
          ),
        ),
      );
}
