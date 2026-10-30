import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'core/api.dart';
import 'core/config.dart';
import 'core/notificaciones.dart';
import 'core/sesion.dart';
import 'pages/acceso.dart';
import 'pages/docente/docente.dart' deferred as docente;
import 'pages/familia/familia.dart' deferred as familia;
import 'ui/tema.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Config.cargar();
  final api = Api();
  final sesion = Sesion(api);
  await sesion.iniciar();
  runApp(MultiProvider(
    providers: [
      Provider<Api>.value(value: api),
      ChangeNotifierProvider<Sesion>.value(value: sesion),
      ChangeNotifierProvider<Notificaciones>(create: (_) => Notificaciones(api)),
    ],
    child: const EduspaceApp(),
  ));
}

final _nav = GlobalKey<NavigatorState>();

class EduspaceApp extends StatelessWidget {
  const EduspaceApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'EduSpace',
        debugShowCheckedModeBanner: false,
        theme: temaEduspace(),
        navigatorKey: _nav,
        home: const _Puerta(),
      );
}

/// Decide qué mostrar según la sesión. Al cerrarse la sesión se vuelve al ingreso desde cualquier pantalla.
class _Puerta extends StatelessWidget {
  const _Puerta();
  @override
  Widget build(BuildContext context) {
    final s = context.watch<Sesion>();
    if (!s.autenticada) {
      context.read<Notificaciones>().detener();
      // Si había pantallas apiladas (detalle, notificaciones…), se cierran para que se vea el ingreso.
      WidgetsBinding.instance.addPostFrameCallback((_) => _nav.currentState?.popUntil((r) => r.isFirst));
      return const PaginaLogin();
    }
    if (s.debeCambiarClave) return const PaginaCambiarClave(obligatorio: true);
    return switch (s.perfil) {
      // El código y los datos de cada rol se bajan recién cuando esa cuenta inicia sesión (la clave `key` fuerza
      // una carga nueva si en el mismo dispositivo entra otra persona).
      Perfil.docente => _PorRol(key: ValueKey('d${s.usuarioId}'), texto: 'Preparando tu espacio de docente…', cargar: docente.loadLibrary, construir: () => docente.AppDocente()),
      Perfil.alumno => _PorRol(key: ValueKey('a${s.usuarioId}'), texto: 'Descargando tu información de alumno…', cargar: familia.loadLibrary, construir: () => familia.AppFamilia()),
      Perfil.apoderado => _PorRol(key: ValueKey('p${s.usuarioId}'), texto: 'Descargando la información de tus hijos…', cargar: familia.loadLibrary, construir: () => familia.AppFamilia()),
      Perfil.otro => Scaffold(
          appBar: AppBar(title: const Text('EduSpace'), actions: [IconButton(onPressed: s.salir, icon: const Icon(Icons.logout))]),
          body: const Center(child: Padding(padding: EdgeInsets.all(24), child: Text('Esta app es para docentes, alumnos y apoderados. El personal administrativo usa la web de EduSpace.', textAlign: TextAlign.center))),
        ),
    };
  }
}

/// Pantalla mínima mientras se descarga lo que corresponde al rol de la cuenta.
class _PorRol extends StatefulWidget {
  final String texto;
  final Future<void> Function() cargar;
  final Widget Function() construir;
  const _PorRol({super.key, required this.texto, required this.cargar, required this.construir});
  @override
  State<_PorRol> createState() => _PorRolState();
}

class _PorRolState extends State<_PorRol> {
  late Future<void> _f = widget.cargar();

  @override
  Widget build(BuildContext context) => FutureBuilder<void>(
        future: _f,
        builder: (c, s) {
          if (s.connectionState == ConnectionState.done && !s.hasError) return widget.construir();
          return Scaffold(
            body: Center(
              child: s.hasError
                  ? Column(mainAxisSize: MainAxisSize.min, children: [
                      const Icon(Icons.cloud_off, size: 44),
                      const SizedBox(height: 10),
                      const Text('No se pudo descargar tu espacio.'),
                      const SizedBox(height: 10),
                      FilledButton(onPressed: () => setState(() => _f = widget.cargar()), child: const Text('Reintentar')),
                      TextButton(onPressed: () => c.read<Sesion>().salir(), child: const Text('Salir')),
                    ])
                  : Column(mainAxisSize: MainAxisSize.min, children: [
                      const CircularProgressIndicator(),
                      const SizedBox(height: 16),
                      Text(widget.texto),
                    ]),
            ),
          );
        },
      );
}
