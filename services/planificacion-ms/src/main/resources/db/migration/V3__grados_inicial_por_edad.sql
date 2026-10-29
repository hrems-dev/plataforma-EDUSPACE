-- Inicial se organiza por edad (Ciclo II: 3, 4 y 5 años), no por grado.
UPDATE grados g
SET nombre = (g.numero + 2) || ' años'
FROM niveles n
WHERE g.nivel_id = n.id AND n.codigo = 'INICIAL' AND g.nombre = g.numero || '° de Inicial';
