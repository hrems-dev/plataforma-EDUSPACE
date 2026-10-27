-- Garantiza a nivel BD que un alumno no tenga dos intentos EN_CURSO del mismo examen
CREATE UNIQUE INDEX uk_intento_en_curso ON intentos (examen_id, matricula_id) WHERE estado = 'EN_CURSO';
