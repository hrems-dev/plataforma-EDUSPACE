import 'package:intl/intl.dart';

/// Formatos de fecha y hora en español (sin depender de los datos de idioma de `intl`).
const _dias = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const _meses = ['', 'ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

String nombreDia(int d) => (d >= 1 && d <= 7) ? _dias[d] : '';

String hora(String h) => h.length >= 5 ? h.substring(0, 5) : h;

String fechaCorta(String iso) {
  final d = DateTime.tryParse(iso);
  return d == null ? iso : '${d.day} ${_meses[d.month]}';
}

String fechaHoraCorta(String iso) {
  final d = DateTime.tryParse(iso);
  if (d == null) return iso;
  return '${d.day} ${_meses[d.month]} · ${DateFormat('HH:mm').format(d)}';
}

String fechaIso(DateTime d) => DateFormat('yyyy-MM-dd').format(d);

String soles(num v) => 'S/ ${v.toStringAsFixed(2)}';

/// «Apellidos, Nombres» → «Nombres Apellidos».
String nombreNatural(String? completo) {
  if (completo == null) return '';
  final i = completo.indexOf(',');
  return i < 0 ? completo.trim() : '${completo.substring(i + 1).trim()} ${completo.substring(0, i).trim()}';
}

String primerNombre(String? completo) => nombreNatural(completo).split(' ').first;

/// «hace 5 min», «hace 2 h», «ayer»…
String hace(String iso) {
  final d = DateTime.tryParse(iso);
  if (d == null) return '';
  final dif = DateTime.now().difference(d);
  if (dif.inMinutes < 1) return 'ahora';
  if (dif.inMinutes < 60) return 'hace ${dif.inMinutes} min';
  if (dif.inHours < 24) return 'hace ${dif.inHours} h';
  if (dif.inDays == 1) return 'ayer';
  return fechaCorta(iso);
}
