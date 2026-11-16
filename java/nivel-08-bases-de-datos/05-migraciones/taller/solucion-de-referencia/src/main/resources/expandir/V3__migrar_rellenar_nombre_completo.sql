-- Despliegue 2 (migrar): copia lo que escribió la versión 1. En tablas grandes, por lotes (ver ColumnaObligatoriaTest).
UPDATE clientes SET nombre_completo = nombre WHERE nombre_completo IS NULL;
