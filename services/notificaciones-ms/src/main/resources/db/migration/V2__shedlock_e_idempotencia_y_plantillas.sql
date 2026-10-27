-- =====================================================================
-- notificaciones_db | V2 (ADENDA) — se agrega sin tocar V1
-- 1) ShedLock: el ReintentoScheduler no debe correr en dos réplicas a la vez.
-- 2) eventos_procesados: idempotencia por eventId (sobre estándar de eventos).
-- 3) Plantillas para eventos nuevos del resto de servicios.
-- =====================================================================
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE eventos_procesados (
    event_id      UUID        PRIMARY KEY,
    event_type    VARCHAR(80) NOT NULL,
    procesado_en  TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_eventos_proc_fecha ON eventos_procesados (procesado_en);

INSERT INTO plantillas_notificacion (codigo, canal, asunto, cuerpo) VALUES
 ('MATRICULA_CONFIRMADA','IN_APP','Matrícula confirmada','La matrícula de {{alumno}} fue confirmada. Sección: {{seccion}}.'),
 ('MATRICULA_ANULADA',   'IN_APP','Matrícula anulada',    'La pre-matrícula de {{alumno}} fue anulada: {{motivo}}.'),
 ('PAGO_POR_VENCER',     'IN_APP','Pago por vencer',      'La cuota "{{concepto}}" de {{alumno}} vence el {{fecha}} (S/ {{monto}}).'),
 ('EXAMEN_PUBLICADO',    'IN_APP','Nuevo examen',         'Examen de {{curso}}: {{titulo}}. Disponible del {{fecha}}.'),
 ('JUSTIFICACION_RESUELTA','IN_APP','Justificación resuelta','La justificación de {{alumno}} fue {{resultado}}.');
