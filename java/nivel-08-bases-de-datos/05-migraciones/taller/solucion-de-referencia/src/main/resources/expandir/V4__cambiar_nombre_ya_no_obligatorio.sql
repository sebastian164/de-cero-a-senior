-- Despliegue 3 (cambiar): la versión 4 ya no escribe "nombre"; deja de ser obligatoria. La nueva sí lo es.
ALTER TABLE clientes ALTER COLUMN nombre DROP NOT NULL;
ALTER TABLE clientes ALTER COLUMN nombre_completo SET NOT NULL;
