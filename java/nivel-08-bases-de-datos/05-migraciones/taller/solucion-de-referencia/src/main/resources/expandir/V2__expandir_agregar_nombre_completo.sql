-- Despliegue 1 (expandir): columna nueva, nula. La versión 1 de la aplicación no la conoce y sigue funcionando.
ALTER TABLE clientes ADD COLUMN nombre_completo VARCHAR(200);
