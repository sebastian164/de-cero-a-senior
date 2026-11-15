# Plan: búsqueda de notas

Especificación: `especificacion.md`

## Decisiones

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| El filtro se aplica en `GET /api/notas`, leyendo `q` de la consulta | Criterios 1 y 9: misma ruta y misma forma de respuesta | Una ruta `/api/notas/buscar`: duplicaría la forma de respuesta |
| Se filtra en memoria sobre las líneas que ya se leen del archivo | Restricción: sin dependencias; hoy ya se lee el archivo entero en cada petición | Un índice: complejidad sin un problema medido |
| Normalizar es una función pura `normalizar(texto)`: minúsculas y sin marcas diacríticas | Criterio 3; se prueba sin levantar el servidor | Expresiones regulares con banderas: el texto buscado no es un patrón (caso límite 1) |
| La comparación es `normalizar(nota).contains(normalizar(q))` | Criterio 4 y caso límite 1: texto literal | — |

## Archivos que cambian

- `Servidor.java`: el manejador de `/api/notas` y dos funciones nuevas.

## Riesgos

- El parámetro llega codificado en la URL: hay que decodificarlo antes de normalizar.
- La lectura del archivo completo en cada búsqueda no escala. Está aceptado por la restricción y anotado en preguntas abiertas.

## Lo que este plan no hace

Nada de "fuera de alcance". En particular, no cambia la forma de la respuesta.
