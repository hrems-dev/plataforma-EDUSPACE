-- =====================================================================
-- notificaciones_db | V3 — se agrega sin tocar V1 ni V2
-- 1) Plantilla del aviso a docentes por horario.publicado (el informe la pide
--    pero V1/V2 no la sembraron).
-- 2) Plantillas EMAIL de los eventos que más importan a la familia: pagos,
--    matrícula y boletín. Sin plantilla EMAIL el servicio solo notifica en la app.
-- =====================================================================
INSERT INTO plantillas_notificacion (codigo, canal, asunto, cuerpo) VALUES
 ('HORARIO_PUBLICADO', 'IN_APP', 'Horario publicado',
  'Se publicó el horario de {{seccion}} (versión {{version}}). Revísalo en la agenda.'),
 ('PAGO_CONFIRMADO',     'EMAIL', 'Pago confirmado — EduSpace',
  'Hemos recibido el pago de S/ {{monto}} por {{concepto}}. Gracias.'),
 ('PAGO_VENCIDO',        'EMAIL', 'Cuota vencida — EduSpace',
  'La cuota "{{concepto}}" de {{alumno}} venció el {{fecha}}. Por favor regularice el pago.'),
 ('PAGO_POR_VENCER',     'EMAIL', 'Cuota por vencer — EduSpace',
  'La cuota "{{concepto}}" de {{alumno}} vence el {{fecha}} (S/ {{monto}}).'),
 ('MATRICULA_CONFIRMADA','EMAIL', 'Matrícula confirmada — EduSpace',
  'La matrícula de {{alumno}} fue confirmada. Sección: {{seccion}}.'),
 ('BOLETIN_GENERADO',    'EMAIL', 'Boletín disponible — EduSpace',
  'El boletín de {{periodo}} de {{alumno}} ya está disponible en la plataforma.');
