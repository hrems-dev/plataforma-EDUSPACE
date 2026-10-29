-- =====================================================================
-- Datos iniciales de uso para planificacion_db (catálogo del colegio).
-- Es idempotente: se puede ejecutar varias veces sin duplicar nada.
-- No va en Flyway a propósito: las pruebas de integración crean sus
-- propios cursos y bloques y chocarían con estos códigos.
--
--   docker exec -i eduspace-planificacion-db \
--     psql -U eduspace -d planificacion_db < sql/10_datos_planificacion.sql
-- =====================================================================

-- ---------- Niveles y grados: Inicial 3, Primaria 6, Secundaria 5 ----------
INSERT INTO niveles (codigo, nombre, orden) VALUES
    ('INICIAL',    'Inicial',    1),
    ('PRIMARIA',   'Primaria',   2),
    ('SECUNDARIA', 'Secundaria', 3)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO grados (nivel_id, numero, nombre)
SELECT n.id, g.numero, g.numero || '° de ' || n.nombre
FROM niveles n
JOIN LATERAL generate_series(1, CASE n.codigo WHEN 'INICIAL' THEN 3 WHEN 'PRIMARIA' THEN 6 ELSE 5 END) AS g(numero) ON TRUE
ON CONFLICT (nivel_id, numero) DO NOTHING;

-- ---------- Cursos / áreas ----------
INSERT INTO cursos (codigo, nombre, area) VALUES
    ('COM',  'Comunicación',                         'Comunicación'),
    ('MAT',  'Matemática',                           'Matemática'),
    ('CYT',  'Ciencia y Tecnología',                 'Ciencia y Tecnología'),
    ('PSO',  'Personal Social',                      'Personal Social'),
    ('CCSS', 'Ciencias Sociales',                    'Personal Social'),
    ('DPCC', 'Desarrollo Personal, Ciudadanía y Cívica', 'Personal Social'),
    ('ART',  'Arte y Cultura',                       'Arte y Cultura'),
    ('EFI',  'Educación Física',                     'Educación Física'),
    ('REL',  'Educación Religiosa',                  'Educación Religiosa'),
    ('ING',  'Inglés',                               'Inglés'),
    ('EPT',  'Educación para el Trabajo',            'Educación para el Trabajo'),
    ('PSM',  'Psicomotricidad',                      'Psicomotricidad'),
    ('TUT',  'Tutoría',                              'Tutoría')
ON CONFLICT (codigo) DO NOTHING;

-- ---------- Malla curricular (horas semanales por nivel) ----------
INSERT INTO grado_cursos (grado_id, curso_id, horas_semanales)
SELECT g.id, c.id, m.horas
FROM (VALUES
    ('INICIAL',    'COM',  4), ('INICIAL',    'MAT',  4), ('INICIAL',    'PSO',  3),
    ('INICIAL',    'CYT',  3), ('INICIAL',    'PSM',  3), ('INICIAL',    'ART',  2),
    ('INICIAL',    'REL',  1),
    ('PRIMARIA',   'COM',  5), ('PRIMARIA',   'MAT',  5), ('PRIMARIA',   'PSO',  3),
    ('PRIMARIA',   'CYT',  3), ('PRIMARIA',   'ART',  2), ('PRIMARIA',   'EFI',  2),
    ('PRIMARIA',   'REL',  2), ('PRIMARIA',   'ING',  3), ('PRIMARIA',   'TUT',  1),
    ('SECUNDARIA', 'COM',  5), ('SECUNDARIA', 'MAT',  5), ('SECUNDARIA', 'CCSS', 3),
    ('SECUNDARIA', 'DPCC', 2), ('SECUNDARIA', 'CYT',  4), ('SECUNDARIA', 'ART',  2),
    ('SECUNDARIA', 'EFI',  2), ('SECUNDARIA', 'REL',  2), ('SECUNDARIA', 'ING',  3),
    ('SECUNDARIA', 'EPT',  2), ('SECUNDARIA', 'TUT',  1)
) AS m(nivel, curso, horas)
JOIN niveles n ON n.codigo = m.nivel
JOIN grados  g ON g.nivel_id = n.id
JOIN cursos  c ON c.codigo = m.curso
ON CONFLICT (grado_id, curso_id) DO NOTHING;

-- ---------- Bloques horarios ----------
INSERT INTO bloques_horarios (turno, orden, hora_inicio, hora_fin, es_receso) VALUES
    ('MANANA', 1, '08:00', '08:45', FALSE),
    ('MANANA', 2, '08:45', '09:30', FALSE),
    ('MANANA', 3, '09:30', '10:15', FALSE),
    ('MANANA', 4, '10:15', '10:35', TRUE),
    ('MANANA', 5, '10:35', '11:20', FALSE),
    ('MANANA', 6, '11:20', '12:05', FALSE),
    ('MANANA', 7, '12:05', '12:50', FALSE),
    ('TARDE',  1, '14:00', '14:45', FALSE),
    ('TARDE',  2, '14:45', '15:30', FALSE),
    ('TARDE',  3, '15:30', '16:15', FALSE),
    ('TARDE',  4, '16:15', '16:35', TRUE),
    ('TARDE',  5, '16:35', '17:20', FALSE),
    ('TARDE',  6, '17:20', '18:05', FALSE)
ON CONFLICT (turno, orden) DO NOTHING;

-- ---------- Aulas ----------
INSERT INTO aulas (codigo, nombre, tipo, capacidad) VALUES
    ('I-3A',  'Aula Inicial 3 años A',  'AULA',        20),
    ('I-4A',  'Aula Inicial 4 años A',  'AULA',        20),
    ('I-5A',  'Aula Inicial 5 años A',  'AULA',        20),
    ('A-1A',  'Aula 1° Primaria A',     'AULA',        30),
    ('A-2A',  'Aula 2° Primaria A',     'AULA',        30),
    ('A-3A',  'Aula 3° Primaria A',     'AULA',        30),
    ('A-4A',  'Aula 4° A',              'AULA',        30),
    ('A-5A',  'Aula 5° Primaria A',     'AULA',        30),
    ('A-6A',  'Aula 6° Primaria A',     'AULA',        30),
    ('S-1A',  'Aula 1° Secundaria A',   'AULA',        35),
    ('S-2A',  'Aula 2° Secundaria A',   'AULA',        35),
    ('S-3A',  'Aula 3° Secundaria A',   'AULA',        35),
    ('S-4A',  'Aula 4° Secundaria A',   'AULA',        35),
    ('S-5A',  'Aula 5° Secundaria A',   'AULA',        35),
    ('LAB-1', 'Laboratorio de Ciencias','LABORATORIO', 30),
    ('COM-1', 'Sala de Cómputo',        'COMPUTO',     30),
    ('AUD-1', 'Auditorio',              'AUDITORIO',  200),
    ('PAT-1', 'Patio principal',        'PATIO',      300)
ON CONFLICT (codigo) DO NOTHING;
