-- Distingue lo que el docente registró de lo precargado por defecto (para el cierre nocturno)
ALTER TABLE asistencia_detalle ADD COLUMN confirmado BOOLEAN NOT NULL DEFAULT FALSE;
-- Para no republicar la notificación de falta del mismo detalle
ALTER TABLE asistencia_detalle ADD COLUMN falta_notificada BOOLEAN NOT NULL DEFAULT FALSE;
