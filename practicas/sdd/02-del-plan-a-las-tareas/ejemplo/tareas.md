# Tareas: búsqueda de notas

Cada tarea: primero la prueba, después el código. Al terminar cada una, todo lo anterior sigue pasando.

| # | Tarea | Criterios | Cómo se comprueba |
|---|---|---|---|
| 1 | Función pura `normalizar`: minúsculas, sin tildes, sin espacios en los extremos | 3, caso límite 2 | Pruebas unitarias: "Café" y "CAFÉ" dan "cafe"; " pan " da "pan" |
| 2 | Función pura `coincide(nota, consulta)` | 3, 4, caso límite 1 | Pruebas unitarias: parcial, tildes, y `a.b` no coincide con "axb" |
| 3 | Leer `q` de la consulta, decodificado | 2 | Prueba de API: `?q=caf%C3%A9` encuentra "café" |
| 4 | Sin `q`, vacío o solo espacios: respuesta idéntica a la actual | 1, 7 | Las pruebas de API existentes pasan sin tocarlas, más dos nuevas |
| 5 | Filtrar conservando el orden | 2, 5 | Prueba de API: tres notas, dos coinciden, llegan en el orden guardado |
| 6 | Sin coincidencias: 200 y lista vacía | 6, caso límite 3 | Prueba de API |
| 7 | `q` de más de 100 caracteres: 400 con cuerpo de error | 8 | Prueba de API con 100 y con 101 caracteres |
| 8 | Verificación final contra la especificación | Todos | Tabla criterio por criterio con su prueba |

## Trazabilidad

| Criterio | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|
| Tareas | 4 | 3, 5 | 1, 2 | 2 | 5 | 6 | 4 | 7 | 4, 5, 6 |
