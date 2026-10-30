import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/config.dart';
import '../core/sesion.dart';
import '../ui/widgets.dart';

class PaginaLogin extends StatefulWidget {
  const PaginaLogin({super.key});
  @override
  State<PaginaLogin> createState() => _PaginaLoginState();
}

class _PaginaLoginState extends State<PaginaLogin> {
  final _u = TextEditingController();
  final _c = TextEditingController();
  bool _enviando = false, _ver = false;
  String? _error;

  Future<void> _entrar() async {
    if (_u.text.trim().isEmpty || _c.text.isEmpty) {
      setState(() => _error = 'Escribe tu usuario y tu clave.');
      return;
    }
    setState(() { _enviando = true; _error = null; });
    try {
      await context.read<Sesion>().login(_u.text, _c.text);
    } on Object catch (e) {
      if (mounted) setState(() => _error = mensajeError(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  Future<void> _servidor() async {
    final ctl = TextEditingController(text: Config.baseUrl);
    final url = await showDialog<String>(
      context: context,
      builder: (c) {
        String? resultado;
        bool probando = false;
        bool? ok;
        return StatefulBuilder(
          builder: (c, set) => AlertDialog(
            title: const Text('Servidor del colegio'),
            content: SingleChildScrollView(
              child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Escribe la dirección del equipo donde corre EduSpace, con el puerto del Gateway. Celular y equipo deben estar en la misma red.', style: TextStyle(fontSize: 13)),
                const SizedBox(height: 8),
                const Text('Ejemplos:\n• Celular: http://192.168.1.20:18080\n• Emulador Android: http://10.0.2.2:18080', style: TextStyle(fontSize: 12.5)),
                const SizedBox(height: 12),
                TextField(controller: ctl, keyboardType: TextInputType.url, autocorrect: false, decoration: const InputDecoration(labelText: 'URL')),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: probando ? null : () async {
                    set(() { probando = true; resultado = null; });
                    final r = await Config.probar(ctl.text);
                    set(() { probando = false; ok = r.$1; resultado = r.$2; });
                  },
                  icon: probando ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.wifi_find),
                  label: const Text('Probar conexión'),
                ),
                if (resultado != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(resultado!, style: TextStyle(fontSize: 13, color: ok == true ? const Color(0xFF059669) : const Color(0xFFDC2626)))),
              ]),
            ),
            actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(c, ctl.text), child: const Text('Guardar'))],
          ),
        );
      },
    );
    if (url != null) {
      await Config.guardar(url);
      if (mounted) setState(() => _error = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<Sesion>();
    final aviso = switch (s.motivoCierre) {
      'vencida' => 'Tu sesión venció. Inicia sesión de nuevo para continuar.',
      'invalida' => 'El sistema ya no reconoce tu sesión (pudo reiniciarse). Inicia sesión de nuevo.',
      _ => null,
    };
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const Icon(Icons.school, size: 56, color: Color(0xFF1F5FBF)),
                const SizedBox(height: 8),
                Text('EduSpace', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
                const Text('Colegio Horizonte del Valle', textAlign: TextAlign.center),
                const SizedBox(height: 28),
                if (aviso != null) Padding(padding: const EdgeInsets.only(bottom: 14), child: _Caja(aviso, Colors.orange)),
                TextField(controller: _u, textInputAction: TextInputAction.next, autocorrect: false, decoration: const InputDecoration(labelText: 'Usuario', hintText: 'nombre.apellido', prefixIcon: Icon(Icons.person_outline))),
                const SizedBox(height: 12),
                TextField(
                  controller: _c, obscureText: !_ver, onSubmitted: (_) => _entrar(),
                  decoration: InputDecoration(labelText: 'Clave', prefixIcon: const Icon(Icons.lock_outline), suffixIcon: IconButton(icon: Icon(_ver ? Icons.visibility_off : Icons.visibility), tooltip: 'Mostrar u ocultar', onPressed: () => setState(() => _ver = !_ver))),
                ),
                if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: _Caja(_error!, Theme.of(context).colorScheme.error)),
                const SizedBox(height: 18),
                FilledButton(onPressed: _enviando ? null : _entrar, child: Text(_enviando ? 'Ingresando…' : 'Ingresar')),
                const SizedBox(height: 8),
                const Text('Tu primera clave es tu DNI; el sistema te pedirá cambiarla.', textAlign: TextAlign.center, style: TextStyle(fontSize: 12.5)),
                TextButton.icon(onPressed: _servidor, icon: const Icon(Icons.settings_ethernet, size: 18), label: Text('Servidor: ${Config.baseUrl}', overflow: TextOverflow.ellipsis)),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}

class _Caja extends StatelessWidget {
  final String texto;
  final Color color;
  const _Caja(this.texto, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: color.withValues(alpha: .1), borderRadius: BorderRadius.circular(12), border: Border.all(color: color.withValues(alpha: .4))),
        child: Text(texto, style: const TextStyle(fontSize: 13.5)),
      );
}

/// Cambio de clave. Es obligatorio la primera vez (`obligatorio`) y voluntario desde el perfil.
class PaginaCambiarClave extends StatefulWidget {
  final bool obligatorio;
  const PaginaCambiarClave({super.key, this.obligatorio = false});
  @override
  State<PaginaCambiarClave> createState() => _PaginaCambiarClaveState();
}

class _PaginaCambiarClaveState extends State<PaginaCambiarClave> {
  final _a = TextEditingController(), _n = TextEditingController(), _r = TextEditingController();
  bool _enviando = false;
  String? _error;

  Future<void> _guardar() async {
    if (_a.text.isEmpty) return setState(() => _error = 'Escribe tu clave actual.');
    if (_n.text.length < 8) return setState(() => _error = 'La clave nueva debe tener al menos 8 caracteres.');
    if (_n.text != _r.text) return setState(() => _error = 'Las claves nuevas no coinciden.');
    if (_n.text == _a.text) return setState(() => _error = 'La clave nueva debe ser distinta de la actual.');
    setState(() { _enviando = true; _error = null; });
    try {
      final nav = Navigator.of(context);
      await context.read<Sesion>().cambiarClave(_a.text, _n.text);
      if (nav.canPop()) nav.pop();
    } on Object catch (e) {
      if (mounted) setState(() => _error = mensajeError(e));
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.obligatorio ? 'Crea tu clave' : 'Cambiar clave'), automaticallyImplyLeading: !widget.obligatorio, actions: [if (widget.obligatorio) TextButton(onPressed: () => context.read<Sesion>().salir(), child: const Text('Salir'))]),
        body: ListView(padding: const EdgeInsets.all(20), children: [
          if (widget.obligatorio) const Padding(padding: EdgeInsets.only(bottom: 16), child: Text('Es tu primer ingreso (o un administrador restauró tu cuenta). Elige una clave personal para continuar.')),
          TextField(controller: _a, obscureText: true, decoration: const InputDecoration(labelText: 'Clave actual')),
          const SizedBox(height: 12),
          TextField(controller: _n, obscureText: true, decoration: const InputDecoration(labelText: 'Clave nueva (mín. 8 caracteres)')),
          const SizedBox(height: 12),
          TextField(controller: _r, obscureText: true, decoration: const InputDecoration(labelText: 'Repite la clave nueva')),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: _Caja(_error!, Theme.of(context).colorScheme.error)),
          const SizedBox(height: 18),
          FilledButton(onPressed: _enviando ? null : _guardar, child: Text(_enviando ? 'Guardando…' : 'Guardar clave')),
          const SizedBox(height: 10),
          const Text('Al cambiarla se cierran tus otras sesiones y vuelves a ingresar con la clave nueva.', style: TextStyle(fontSize: 12.5)),
        ]),
      );
}
