-- Patrón Outbox: el evento se guarda en la misma transacción que el cambio de negocio
-- y un relay lo publica a Kafka con reintentos. Evita perder eventos si Kafka cae tras el commit.
CREATE TABLE outbox_eventos (
    id            BIGSERIAL PRIMARY KEY,
    topico        VARCHAR(120) NOT NULL,
    clave         VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    creado_en     TIMESTAMP    NOT NULL DEFAULT now(),
    enviado_en    TIMESTAMP,
    intentos      INTEGER      NOT NULL DEFAULT 0,
    ultimo_error  VARCHAR(500)
);
CREATE INDEX idx_outbox_pendientes ON outbox_eventos (id) WHERE enviado_en IS NULL;
