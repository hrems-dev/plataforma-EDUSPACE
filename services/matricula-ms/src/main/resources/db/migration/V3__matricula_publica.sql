-- Matrícula en línea desde el sitio público: guarda lo mínimo para retomar el proceso (pagar, ver el resumen, bajar la constancia).
-- El token se guarda como hash SHA-256: quien lo conoce es quien inició el trámite. No se guardan datos de tarjeta.
CREATE TABLE matriculas_publicas (
    matricula_id        BIGINT       PRIMARY KEY REFERENCES matriculas (id),
    token_hash          VARCHAR(64)  NOT NULL,
    apoderado_id        BIGINT       NOT NULL,
    apoderado_usuario_id BIGINT,
    apoderado_nombre    VARCHAR(200) NOT NULL,
    apoderado_documento VARCHAR(20)  NOT NULL,
    apoderado_email     VARCHAR(150),
    alumno_nombres      VARCHAR(100) NOT NULL,
    alumno_apellidos    VARCHAR(160) NOT NULL,
    alumno_documento    VARCHAR(20)  NOT NULL,
    estado              VARCHAR(12)  NOT NULL DEFAULT 'INICIADA',   -- INICIADA | PAGADA
    pago_id             BIGINT,
    comprobante         VARCHAR(30),
    monto_matricula     NUMERIC(10,2),
    alumno_usuario      VARCHAR(80),
    apoderado_usuario   VARCHAR(80),
    correo_estado       VARCHAR(12),                                 -- ENVIADO | OMITIDO | ERROR
    creado_en           TIMESTAMP    NOT NULL DEFAULT now(),
    pagada_en           TIMESTAMP,
    CONSTRAINT ck_mp_estado CHECK (estado IN ('INICIADA', 'PAGADA'))
);
CREATE INDEX idx_mp_documento ON matriculas_publicas (alumno_documento);
