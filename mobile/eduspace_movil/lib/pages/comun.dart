import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/formato.dart';
import '../core/notificaciones.dart';
import '../core/sesion.dart';
import '../ui/widgets.dart';
import 'acceso.dart';

class PaginaNotificaciones extends StatefulWidget {
  const PaginaNotificaciones({super.key});
  @override
  State<PaginaNotificaciones> createState() => _PaginaNotificacionesState();
}

class _PaginaNotificacionesState extends State<PaginaNotificaciones> {
  final _items = <Map<String, dynamic>>[];
  int _pag = 0, _total = 1;
  bool _cargando = true, _soloNoLeidas = false;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _recargar();
  }

  Future<void> _recargar() async {
    _items.clear();
    _pag = 0;
    _total = 1;
    await _mas();
  }

  Future<void> _mas() async {
    if (_pag >= _total && _items.isNotEmpty) return;
    setState(() { _cargando = true; _error = null; });
    try {
      final r = await context.read<Notificaciones>().pagina(_pag, leida: _soloNoLeidas ? false : null);
      _items.addAll(List<Map<String, dynamic>>.from((r['contenido'] as List).map((e) => Map<String, dynamic>.from(e as Map))));
      _total = (r['totalPaginas'] as num?)?.toInt() ?? 1;
      _pag++;
    } on Object catch (e) {
      _error = e;
    }
    if (mounted) setState(() => _cargando = false);
  }

  IconData _icono(String tipo) {
    final t = tipo.toUpperCase();
    if (t.contains('TAREA')) return Icons.assignment_outlined;
    if (t.contains('ASISTENCIA') || t.contains('FALTA')) return Icons.fact_check_outlined;
    if (t.contains('NOTA') || t.contains('CALIF') || t.contains('BOLETIN')) return Icons.grade_outlined;
    if (t.contains('PAGO') || t.contains('CUOTA') || t.contains('MORA')) return Icons.payments_outlined;
    if (t.contains('EXAMEN')) return Icons.quiz_outlined;
    return Icons.notifications_outlined;
  }

  @override
  Widget build(BuildContext context) {
    final n = context.read<Notificaciones>();
    return Scaffold(
      appBar: AppBar(title: const Text('Notificaciones'), actions: [
        IconButton(
          tooltip: _soloNoLeidas ? 'Ver todas' : 'Solo sin leer',
          icon: Icon(_soloNoLeidas ? Icons.mark_email_unread : Icons.mark_email_unread_outlined),
          onPressed: () { _soloNoLeidas = !_soloNoLeidas; _recargar(); },
        ),
        IconButton(
          tooltip: 'Marcar todas como leídas',
          icon: const Icon(Icons.done_all),
          onPressed: () async {
            try { await n.marcarTodas(); await _recargar(); } on Object catch (e) { if (context.mounted) avisar(context, mensajeError(e), error: true); }
          },
        ),
      ]),
      body: RefreshIndicator(
        onRefresh: _recargar,
        child: _items.isEmpty
            ? (_cargando
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Estado(icono: Icons.cloud_off, texto: mensajeError(_error!), accion: 'Reintentar', alAccion: _recargar)
                    : Estado(icono: Icons.notifications_none, texto: _soloNoLeidas ? 'No tienes notificaciones sin leer.' : 'Aún no tienes notificaciones.'))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: _items.length + 1,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (c, i) {
                  if (i == _items.length) {
                    return _pag < _total ? Center(child: TextButton(onPressed: _cargando ? null : _mas, child: Text(_cargando ? 'Cargando…' : 'Ver más'))) : const SizedBox(height: 24);
                  }
                  final x = _items[i];
                  final leida = x['leida'] == true;
                  return Card(
                    child: ListTile(
                      leading: CircleAvatar(backgroundColor: leida ? Colors.grey.shade200 : Theme.of(c).colorScheme.primaryContainer, child: Icon(_icono('${x['tipo']}'))),
                      title: Text('${x['titulo']}', style: TextStyle(fontWeight: leida ? FontWeight.w500 : FontWeight.w800)),
                      subtitle: Text('${x['mensaje']}\n${hace('${x['creadaEn']}')}'),
                      isThreeLine: true,
                      trailing: leida ? null : const Icon(Icons.circle, size: 10, color: Color(0xFF1F5FBF)),
                      onTap: leida ? null : () async {
                        try { await n.marcar((x['id'] as num).toInt()); setState(() => x['leida'] = true); } on Object catch (e) { if (c.mounted) avisar(c, mensajeError(e), error: true); }
                      },
                    ),
                  );
                },
              ),
      ),
    );
  }
}

class PaginaPerfil extends StatelessWidget {
  final String? nombre;
  const PaginaPerfil({super.key, this.nombre});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<Sesion>();
    final rol = switch (s.perfil) { Perfil.docente => 'Docente', Perfil.alumno => 'Alumno', Perfil.apoderado => 'Apoderado', Perfil.otro => 'Usuario' };
    return ListView(padding: const EdgeInsets.all(16), children: [
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            CircleAvatar(radius: 28, child: Text(s.usuario.isEmpty ? '?' : s.usuario[0].toUpperCase(), style: const TextStyle(fontSize: 22))),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              if (nombre != null && nombre!.isNotEmpty) Text(nombre!, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17)),
              Text(s.usuario),
              Insignia(rol, const Color(0xFF1F5FBF)),
            ])),
          ]),
        ),
      ),
      const SizedBox(height: 12),
      Card(
        child: Column(children: [
          ListTile(leading: const Icon(Icons.lock_reset), title: const Text('Cambiar clave'), trailing: const Icon(Icons.chevron_right), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PaginaCambiarClave()))),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.logout), title: const Text('Cerrar sesión'), onTap: () => s.salir()),
        ]),
      ),
      const SizedBox(height: 12),
      const Text('¿Olvidaste tu clave? Pide a secretaría que restaure tu cuenta: volverá a ser tu DNI y se te pedirá cambiarla.', style: TextStyle(fontSize: 12.5)),
    ]);
  }
}

/// Marco común: barra de abajo, campana con contador y banner de servicio caído.
class MarcoApp extends StatefulWidget {
  final List<({String etiqueta, IconData icono, Widget pagina})> destinos;
  final String Function(int) titulo;
  const MarcoApp({super.key, required this.destinos, required this.titulo});
  @override
  State<MarcoApp> createState() => _MarcoAppState();
}

class _MarcoAppState extends State<MarcoApp> with WidgetsBindingObserver {
  int _i = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    context.read<Notificaciones>().iniciar();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) context.read<Notificaciones>().refrescar();
  }

  @override
  Widget build(BuildContext context) {
    final caido = context.select<Sesion, bool>((s) => s.servicioCaido);
    final n = context.watch<Notificaciones>().noLeidas;
    return Scaffold(
      appBar: AppBar(title: Text(widget.titulo(_i)), actions: [
        IconButton(
          tooltip: 'Notificaciones',
          icon: Badge(isLabelVisible: n > 0, label: Text(n > 9 ? '9+' : '$n'), child: const Icon(Icons.notifications_outlined)),
          onPressed: () async {
            await Navigator.push(context, MaterialPageRoute(builder: (_) => const PaginaNotificaciones()));
            if (context.mounted) context.read<Notificaciones>().refrescar();
          },
        ),
      ]),
      body: Column(children: [
        if (caido) const AvisoServicio(),
        Expanded(child: IndexedStack(index: _i, children: [for (final d in widget.destinos) d.pagina])),
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _i,
        onDestinationSelected: (v) => setState(() => _i = v),
        destinations: [for (final d in widget.destinos) NavigationDestination(icon: Icon(d.icono), label: d.etiqueta)],
      ),
    );
  }
}
