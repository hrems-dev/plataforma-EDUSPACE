import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api.dart';
import '../../core/formato.dart';
import '../../core/sesion.dart';
import '../../ui/widgets.dart';
import '../comun.dart';
import 'datos.dart';
import 'secciones.dart';

class AppFamilia extends StatelessWidget {
  const AppFamilia({super.key});
  @override
  Widget build(BuildContext context) {
    final apoderado = context.read<Sesion>().perfil == Perfil.apoderado;
    return ChangeNotifierProvider(
      create: (c) => FamiliaDatos(c.read<Api>(), apoderado)..cargar().catchError((_) {}),
      child: Consumer<FamiliaDatos>(builder: (c, f, _) => _Contenido(f: f, apoderado: apoderado)),
    );
  }
}

class _Contenido extends StatefulWidget {
  final FamiliaDatos f;
  final bool apoderado;
  const _Contenido({required this.f, required this.apoderado});
  @override
  State<_Contenido> createState() => _ContenidoState();
}

class _ContenidoState extends State<_Contenido> {
  Object? _error;
  @override
  void initState() {
    super.initState();
    if (!widget.f.cargado) widget.f.cargar().catchError((e) { if (mounted) setState(() => _error = e); });
  }

  @override
  Widget build(BuildContext context) {
    final f = widget.f;
    if (!f.cargado) {
      return Scaffold(
        appBar: AppBar(title: const Text('EduSpace')),
        body: _error != null
            ? Estado(icono: Icons.cloud_off, texto: mensajeError(_error!), accion: 'Reintentar', alAccion: () { setState(() => _error = null); f.cargar().catchError((e) { if (mounted) setState(() => _error = e); }); })
            : const Center(child: CircularProgressIndicator()),
      );
    }
    if (f.alumnos.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('EduSpace'), actions: [IconButton(onPressed: () => context.read<Sesion>().salir(), icon: const Icon(Icons.logout))]),
        body: const Estado(icono: Icons.child_care, texto: 'Tu cuenta aún no está vinculada a un alumno. Pide a secretaría que la vincule.'),
      );
    }
    final key = ValueKey('${f.alumnoId}');
    return MarcoApp(
      key: key,
      titulo: (i) => ['Inicio', 'Tareas', 'Horario', 'Notas', 'Más'][i],
      destinos: [
        (etiqueta: 'Inicio', icono: Icons.home_outlined, pagina: _ConHijo(f: f, hijo: PaginaInicioFamilia(f: f))),
        (etiqueta: 'Tareas', icono: Icons.assignment_outlined, pagina: _ConHijo(f: f, hijo: PaginaTareasFamilia(f: f))),
        (etiqueta: 'Horario', icono: Icons.calendar_view_week, pagina: _ConHijo(f: f, hijo: PaginaHorarioFamilia(f: f))),
        (etiqueta: 'Notas', icono: Icons.grade_outlined, pagina: _ConHijo(f: f, hijo: PaginaNotasFamilia(f: f))),
        (etiqueta: 'Más', icono: Icons.menu, pagina: PaginaMasFamilia(f: f, apoderado: widget.apoderado)),
      ],
    );
  }
}

/// Selector de hijo (sólo si hay más de uno) sobre el contenido.
class _ConHijo extends StatelessWidget {
  final FamiliaDatos f;
  final Widget hijo;
  const _ConHijo({required this.f, required this.hijo});
  @override
  Widget build(BuildContext context) => Column(children: [
        if (f.alumnos.length > 1)
          SizedBox(
            height: 52,
            child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), children: [
              for (var i = 0; i < f.alumnos.length; i++)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(label: Text(primerNombre('${f.alumnos[i]['nombreCompleto']}')), selected: f.seleccionado == i, onSelected: (_) => f.elegir(i)),
                ),
            ]),
          ),
        Expanded(child: KeyedSubtree(key: ValueKey('h${f.alumnoId}'), child: hijo)),
      ]);
}

class PaginaMasFamilia extends StatelessWidget {
  final FamiliaDatos f;
  final bool apoderado;
  const PaginaMasFamilia({super.key, required this.f, required this.apoderado});
  @override
  Widget build(BuildContext context) {
    void abrir(String t, Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: Text(t)), body: w)));
    return ListView(padding: const EdgeInsets.all(16), children: [
      Card(
        child: Column(children: [
          ListTile(leading: const Icon(Icons.fact_check_outlined), title: const Text('Asistencia'), subtitle: Text(apoderado ? 'Resumen y justificaciones' : 'Tu asistencia'), trailing: const Icon(Icons.chevron_right), onTap: () => abrir('Asistencia', PaginaAsistenciaFamilia(f: f, apoderado: apoderado))),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.folder_copy_outlined), title: const Text('Materiales'), subtitle: const Text('Guías, presentaciones y enlaces de tus docentes'), trailing: const Icon(Icons.chevron_right), onTap: () => abrir('Materiales', PaginaMaterialesFamilia(f: f))),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.quiz_outlined), title: const Text('Exámenes'), subtitle: const Text('Disponibles para rendir'), trailing: const Icon(Icons.chevron_right), onTap: () => abrir('Exámenes', PaginaExamenesFamilia(f: f))),
          if (apoderado) ...[
            const Divider(height: 1),
            ListTile(leading: const Icon(Icons.payments_outlined), title: const Text('Pagos'), subtitle: const Text('Cuotas y pago en línea'), trailing: const Icon(Icons.chevron_right), onTap: () => abrir('Pagos', PaginaPagosFamilia(f: f))),
          ],
        ]),
      ),
      const SizedBox(height: 14),
      SizedBox(height: 380, child: PaginaPerfil(nombre: apoderado ? null : f.nombre)),
    ]);
  }
}
