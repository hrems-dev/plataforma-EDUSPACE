# EduSpace móvil

App Flutter para **docentes, alumnos y apoderados** (una sola app, pantallas según el rol). Notificaciones para todos.

Documentación: [`docs/movil/`](../../docs/movil/00-analisis-por-rol.md) (análisis por rol e instalación).

```bash
flutter pub get
flutter analyze && flutter test
flutter run -d chrome --web-port 5000   # o en un emulador / celular
```

Servidor por defecto: `http://localhost:18080` (web) y `http://10.0.2.2:18080` (emulador Android); se cambia desde la pantalla de ingreso.
