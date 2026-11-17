# Cambio 005: como máximo cinco etiquetas por nota

## Motivo

Notas con diez etiquetas se leen como una nube de palabras y no ayudan a agrupar. Se pide un máximo de cinco.

## Cambio en la especificación

- Criterio 6, antes: "Si una nota contiene más de 10 etiquetas distintas, entonces el sistema deberá responder 400…"
- Criterio 6, ahora: "Si una nota contiene más de 5 etiquetas distintas, entonces el sistema deberá responder 400…"
- Marca `limite-etiquetas`: de 10 a 5.

## Lo que no cambia

Todo lo demás. En particular, la forma del error y que las etiquetas repetidas cuentan una sola vez.

## Pruebas afectadas

- `etiquetas.feature`, `@criterio-6`: los límites pasan de 10/11 a 5/6.

## Riesgo

Las notas ya guardadas con entre 6 y 10 etiquetas siguen existiendo y se pueden filtrar: el límite solo aplica al guardar.
