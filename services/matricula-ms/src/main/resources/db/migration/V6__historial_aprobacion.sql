-- El historial registra el pago, la aprobación y el rechazo de la matrícula
ALTER TABLE matricula_historial DROP CONSTRAINT ck_hist_tipo;
ALTER TABLE matricula_historial ADD CONSTRAINT ck_hist_tipo
    CHECK (tipo_cambio IN ('CREACION','PAGO','APROBACION','RECHAZO','CONFIRMACION','TRASLADO_SECCION','RETIRO','ANULACION'));
