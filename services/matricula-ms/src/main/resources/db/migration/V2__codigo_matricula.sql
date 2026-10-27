-- Correlativo del código de matrícula (MAT-<año>-<000001>)
CREATE SEQUENCE matricula_codigo_seq START 1;
-- Evita doble confirmación/anulación de un mismo pago de matrícula en el historial técnico
CREATE INDEX idx_hist_tipo ON matricula_historial (tipo_cambio);
