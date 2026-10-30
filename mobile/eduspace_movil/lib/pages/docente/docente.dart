import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api.dart';
import '../../core/formato.dart';
import '../../ui/tema.dart';
import '../../ui/widgets.dart';
import '../comun.dart';
import 'asistencia.dart';
import 'datos.dart';
import 'materiales.dart';
import 'tareas.dart';

class AppDocente extends StatelessWidget {
  const AppDocente({super.key});
  @override
  Widget build(BuildContext context) {
    final d = DocenteDatos(context.read<Api>());
    return MarcoApp(
      titulo: (i) => ['Hoy', 'Mis cursos', 'Horario', 'Perfil'][i],
      destinos: [
        (etiqueta: 'Hoy', icono: Icons.today, pagina: _Hoy(d)),
        (etiqueta: 'Cursos', icono: Icons.menu_book_outlined, pagina: _Cursos(d)),
        (etiqueta: 'Horario', icono: Icons.calendar_view_week, pagina: _Semana(d)),
        (etiqueta: 'Perfil', icono: Icons.person_outline, pagina: PaginaPerfil(nombre: d.nombre)),
      ],
    );
  }
}

class _Hoy extends StatelessWidget {
  final DocenteDatos d;
  const _Hoy(this.d);
  @override
  Widget build(BuildContext context) => Cargador<List<Map<String, dynamic>>>(
        cargar: d.clases,
        construir: (c, clases, _) {
          final dia = DateTime.now().weekday;
          final hoy = clases.where((x) => x['diaSemana'] == dia).toList();
          final ahora = DateTime.now();
          final hm = '${ahora.hour.toString().padLeft(2, '0')}:${ahora.minute.toString().padLeft(2, '0')}';
          return ListView(padding: const EdgeInsets.all(16), children: [
            Text('${nombreDia(dia)} ${ahora.day}', style: Theme.of(c).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
            Text(hoy.isEmpty ? 'No tienes clases hoy.' : '${hoy.length} clase(s) hoy. Toca una para pasar lista.'),
            const SizedBox(height: 12),
            for (final x in hoy)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: TarjetaCurso(
                  titulo: '${x['cursoNombre']}',
                  subtitulo: '${hora('${x['horaInicio']}')} – ${hora('${x['horaFin']}')} · ${x['seccionNombre']}${x['aulaNombre'] != null ? ' · ${x['aulaNombre']}' : ''}',
                  color: colorCurso((x['cursoId'] as num).toInt()),
                  extra: hm.compareTo(hora('${x['horaInicio']}')) >= 0 && hm.compareTo(hora('${x['horaFin']}')) <= 0 ? const Insignia('En curso', Color(0xFF059669)) : null,
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PaginaAsistencia(seccionId: (x['seccionId'] as num).toInt(), seccion: '${x['seccionNombre']}', curso: '${x['cursoNombre']}'))),
                ),
              ),
          ]);
        },
      );
}

class _Cursos extends StatelessWidget {
  final DocenteDatos d;
  const _Cursos(this.d);
  @override
  Widget build(BuildContext context) => Cargador<List<Map<String, dynamic>>>(
        cargar: d.cursos,
        construir: (c, cursos, _) => cursos.isEmpty
            ? const Estado(icono: Icons.menu_book_outlined, texto: 'Todavía no tienes cursos asignados.')
            : ListView(padding: const EdgeInsets.all(16), children: [
                for (final x in cursos)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: TarjetaCurso(
                      titulo: '${x['cursoNombre']}',
                      subtitulo: '${x['seccionNombre']}',
                      color: colorCurso((x['cursoId'] as num).toInt()),
                      onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PaginaCursoDocente(d: d, seccionId: (x['seccionId'] as num).toInt(), cursoId: (x['cursoId'] as num).toInt(), curso: '${x['cursoNombre']}', seccion: '${x['seccionNombre']}'))),
                    ),
                  ),
              ]),
      );
}

class _Semana extends StatelessWidget {
  final DocenteDatos d;
  const _Semana(this.d);
  @override
  Widget build(BuildContext context) => Cargador<List<Map<String, dynamic>>>(
        cargar: d.clases,
        construir: (c, clases, _) {
          if (clases.isEmpty) return const Estado(icono: Icons.calendar_today, texto: 'Aún no hay un horario publicado para ti.');
          return ListView(padding: const EdgeInsets.all(16), children: [
            for (var dia = 1; dia <= 7; dia++)
              if (clases.any((x) => x['diaSemana'] == dia)) ...[
                Titulo(nombreDia(dia)),
                for (final x in clases.where((x) => x['diaSemana'] == dia))
                  Card(
                    child: ListTile(
                      leading: Container(width: 6, height: 40, decoration: BoxDecoration(color: colorCurso((x['cursoId'] as num).toInt()), borderRadius: BorderRadius.circular(3))),
                      title: Text('${x['cursoNombre']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: Text('${hora('${x['horaInicio']}')} – ${hora('${x['horaFin']}')} · ${x['seccionNombre']}${x['aulaNombre'] != null ? ' · ${x['aulaNombre']}' : ''}'),
                    ),
                  ),
              ],
          ]);
        },
      );
}

/// Un curso del docente: tareas y asistencia en el mismo lugar.
class PaginaCursoDocente extends StatelessWidget {
  final DocenteDatos d;
  final int seccionId, cursoId;
  final String curso, seccion;
  const PaginaCursoDocente({super.key, required this.d, required this.seccionId, required this.cursoId, required this.curso, required this.seccion});
  @override
  Widget build(BuildContext context) => DefaultTabController(
        length: 3,
        child: Scaffold(
          appBar: AppBar(
            title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(curso, style: const TextStyle(fontSize: 18)), Text(seccion, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w400))]),
            backgroundColor: colorCurso(cursoId).withValues(alpha: .12),
            bottom: const TabBar(tabs: [Tab(text: 'Tareas'), Tab(text: 'Materiales'), Tab(text: 'Asistencia')]),
          ),
          body: TabBarView(children: [
            TareasDocente(d: d, seccionId: seccionId, cursoId: cursoId),
            MaterialesDocente(seccionId: seccionId, cursoId: cursoId),
            ResumenAsistencia(seccionId: seccionId, seccion: seccion, curso: curso),
          ]),
        ),
      );
}
