-- Aviso de tareas por vencer (evento tarea.recordatorio de tareas-ms). Aditiva: no toca V1-V3.
INSERT INTO plantillas_notificacion (codigo, canal, asunto, cuerpo) VALUES
 ('TAREA_RECORDATORIO', 'IN_APP', 'Tarea por vencer',
  'Recordatorio: la tarea de {{curso}} "{{titulo}}" vence el {{fecha}} y aún no tiene entrega.');
