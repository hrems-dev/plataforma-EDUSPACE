-- Aprobación administrativa: tras el pago la matrícula queda POR_APROBAR; solo la administración la pasa a MATRICULADO.
ALTER TABLE matriculas DROP CONSTRAINT ck_matricula_estado;
ALTER TABLE matriculas ADD CONSTRAINT ck_matricula_estado
    CHECK (estado IN ('PRE_MATRICULA','POR_APROBAR','MATRICULADO','RETIRADO','ANULADO','TRASLADADO'));

-- Una matrícula pagada y pendiente de aprobación sigue ocupando el cupo del alumno en el año
DROP INDEX uk_matricula_viva;
CREATE UNIQUE INDEX uk_matricula_viva ON matriculas (alumno_id, anio_id)
    WHERE estado IN ('PRE_MATRICULA','POR_APROBAR','MATRICULADO');

ALTER TABLE matriculas ADD COLUMN pagada_en    TIMESTAMP;
ALTER TABLE matriculas ADD COLUMN aprobada_por BIGINT;
ALTER TABLE matriculas ADD COLUMN aprobada_en  TIMESTAMP;

-- Trámite público: INICIADA (sin pago) → PAGADA (en revisión) → APROBADA | RECHAZADA
ALTER TABLE matriculas_publicas DROP CONSTRAINT ck_mp_estado;
ALTER TABLE matriculas_publicas ADD CONSTRAINT ck_mp_estado CHECK (estado IN ('INICIADA','PAGADA','APROBADA','RECHAZADA'));
