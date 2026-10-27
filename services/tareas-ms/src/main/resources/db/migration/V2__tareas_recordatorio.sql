-- Recordatorio de vencimiento: una sola vez por tarea (aunque haya varias réplicas)
ALTER TABLE tareas ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;
