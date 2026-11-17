# Plan: etiquetas en las notas

Especificación: `especificacion.md`. Principios: `principios.md`.

## Decisiones

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Las etiquetas no se guardan: se calculan del texto con una función pura `etiquetas(texto)` | Restricción de no migrar y criterio 14 (las notas antiguas cuentan); la función se prueba sin servidor | Una columna o archivo de etiquetas: exige migración y una segunda fuente de verdad |
| Reconocer con una expresión regular `(?<!\S)#([\p{L}\p{N}_-]+)` y descartar las series de más de 30 | Definiciones y caso límite de 31 caracteres: la serie completa decide, no su prefijo | Limitar la repetición a `{1,30}` en la expresión: reconocería el prefijo de 30 de una serie de 31 |
| Normalizar con `toLowerCase(Locale.ROOT)` | Criterio 2; no depender del idioma de la máquina | `toLowerCase()` sin idioma: en un sistema turco, `I` no daría `i` |
| El filtro se aplica en `GET /api/notas` sobre las líneas que ya se leen | Criterios 12 y 13: misma ruta y misma forma | Una ruta nueva `/api/etiquetas/<e>`: duplica la forma de la respuesta |
| Distinguir "parámetro vacío" de "parámetro ausente" leyendo la consulta sin decodificar | Criterios 11 y 12: `?etiqueta=` es 400, sin parámetro es como hoy | Reutilizar `parametro()` que existe: devuelve el valor por defecto en ambos casos |
| La constante `MAXIMO_ETIQUETAS` en un solo sitio | Un cambio futuro del límite toca una línea | El número repetido en el código |
| Con `etiqueta` repetida en la consulta se usa la primera | Aclaración 7: no es un criterio; es la forma más simple | Rechazar con 400: no pedido |

## Archivos que cambian

- `Servidor.java`: dos constantes, la función `etiquetas`, una función para leer un parámetro de texto, el manejador de `/api/notas`.
- Nuevos: `pruebas/` (Karate) y `PruebaEtiquetas.java`, fuera de la API.

## Riesgos

- La expresión regular con Unicode: se prueba con `ñ`, tildes y guion bajo.
- Leer el archivo completo en cada consulta (ya ocurre hoy).
- Que el tabulador o el salto de línea cuenten como inicio de palabra: se normalizan al guardar (aclaración 3).

## Lo que este plan no hace

Nada de "fuera de alcance": no hay listado de etiquetas, ni varias a la vez, ni cambios en la web.
