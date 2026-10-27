-- Para entornos sin Docker Compose: crea las bases y el usuario de los 9 servicios con base de datos.
-- (Con Docker, cada compose-dev.yml crea su base vía POSTGRES_DB; auth-ms no tiene código todavía.)
CREATE USER eduspace WITH PASSWORD 'eduspace';
CREATE DATABASE auth_db OWNER eduspace;
CREATE DATABASE planificacion_db OWNER eduspace;
CREATE DATABASE alumnos_db OWNER eduspace;
CREATE DATABASE matricula_db OWNER eduspace;
CREATE DATABASE asistencia_db OWNER eduspace;
CREATE DATABASE calificaciones_db OWNER eduspace;
CREATE DATABASE pago_db OWNER eduspace;
CREATE DATABASE examenes_db OWNER eduspace;
CREATE DATABASE tareas_db OWNER eduspace;
CREATE DATABASE notificaciones_db OWNER eduspace;
