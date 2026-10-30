/// Tipo MIME según la extensión (los tipos que acepta el servidor para entregas y materiales).
String tipoPorNombre(String nombre) {
  final e = nombre.toLowerCase().split('.').last;
  return switch (e) {
    'pdf' => 'application/pdf',
    'png' => 'image/png',
    'jpg' || 'jpeg' => 'image/jpeg',
    'txt' => 'text/plain',
    'zip' => 'application/zip',
    'pptx' => 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'xlsx' => 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    _ => 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  };
}

const extensionesMaterial = ['pdf', 'docx', 'pptx', 'xlsx', 'jpg', 'jpeg', 'png', 'zip', 'txt'];

String tamanoLegible(num bytes) => bytes >= 1048576 ? '${(bytes / 1048576).toStringAsFixed(1)} MB' : '${(bytes / 1024).ceil()} KB';
