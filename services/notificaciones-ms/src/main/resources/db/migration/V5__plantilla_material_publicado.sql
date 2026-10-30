-- Aviso a alumnos y apoderados cuando un docente publica material de un curso
INSERT INTO plantillas_notificacion (codigo, canal, asunto, cuerpo) VALUES
 ('MATERIAL_PUBLICADO', 'IN_APP', 'Nuevo material', 'Tu docente publicó material en {{curso}}: {{titulo}}.');
