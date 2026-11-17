# Tareas: etiquetas en las notas

Cada tarea: primero la prueba, después el código. Al terminar cada una, todo lo anterior sigue pasando y el servidor arranca.

| # | Tarea | Criterios | Cómo se comprueba | Revisión de la tabla "buena tarea" |
|---|---|---|---|---|
| T1 | Función pura `etiquetas(texto)` y las constantes | 1, 2, 3, casos límite | `PruebaEtiquetas` (19 casos) | Se hace en una sentada; dice criterios; tiene prueba; no cambia el comportamiento del servidor |
| T2 | `POST /api/notas` responde `etiquetas` | 4, 5 | Escenarios `@criterio-4` `@criterio-5` y la tabla de reconocimiento | Una sentada: solo la forma de la respuesta; el límite es T3 para que cada una se revise sola |
| T3 | Rechazar con 400 una nota de más de 10 etiquetas distintas, sin guardarla | 6 | Escenarios `@criterio-6` | Una sentada; criterio 6 |
| T4 | `GET /api/notas?etiqueta=<e>` filtra, en orden, sin distinguir mayúsculas ni `#` | 7, 8, 9, 10, 12, 13, 14 | Escenarios `@criterio-7` a `@criterio-14` | Es la más grande (7 criterios): se mantiene junta porque filtrar, ordenar y normalizar no se pueden probar por separado; la validación (T5) va aparte |
| T5 | Validar `<e>`: 400 si vacía, de más de 30 o con caracteres inválidos | 11 | Escenarios `@criterio-11` | Una sentada; criterio 11 |
| T6 | Comprobar que guardar exige clave y consultar no | 15 | Escenarios `@criterio-15` (no requiere código nuevo) | Tarea de verificación: se justifica porque protege una restricción |
| T7 | Verificación final contra la especificación | todos | Tabla criterio por criterio (`ANALISIS.md`) | Cierre |

Tareas que no apuntan a ningún criterio: ninguna.

## Trazabilidad

| Criterio | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Tarea | T1, T2 | T1 | T1 | T2 | T2 | T3 | T4 | T4 | T4 | T4 | T5 | T4 | T4 | T4 | T6 |
| Prueba | `@criterio-1` | `@criterio-2` | `@criterio-3` | `@criterio-4` | `@criterio-5` | `@criterio-6` | `@criterio-7` | `@criterio-8` | `@criterio-9` | `@criterio-10` | `@criterio-11` | `@criterio-12` | `@criterio-13` | `@criterio-14` | `@criterio-15` |
