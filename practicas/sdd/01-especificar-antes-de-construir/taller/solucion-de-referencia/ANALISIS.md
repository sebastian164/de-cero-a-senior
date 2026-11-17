# Taller SDD 01: especificar antes de construir

Archivos: `preguntas.md` (pasos 1 y 2), `especificacion.md` (3, 5), `casos.md` (4, 7), `notas-para-el-plan.md` (6), `karate/etiquetas-escenarios.feature` (9).
`./comprobar.sh` comprueba lo comprobable: 15 criterios con EARS y con al menos un caso cada uno, sin palabras de implementación, 8 secciones, 7 elementos fuera de alcance, 22 preguntas y las 5 abiertas con su valor por defecto, y los tres escenarios.

## Paso 4: criterios reescritos

Redacciones iniciales que no se podían probar sin ambigüedad, y cómo quedaron:

| Antes | Problema | Después |
|---|---|---|
| "Las etiquetas deben ser cortas" | ¿cuánto es corta? | "de 1 a 30 caracteres" (definición) |
| "Se ignoran los caracteres raros" | ¿cuáles? | "letras, dígitos, `_` o `-`; la serie termina en el primer carácter que no es de esos" |
| "Una nota no debe tener demasiadas etiquetas" | ¿cuántas? ¿qué ocurre? | "más de 10 etiquetas distintas: 400 y no se guarda" |
| "Si la etiqueta no existe, se informa" | ¿error o lista vacía? | "200 con una lista vacía" |

## Paso 7: la prueba de las dos personas

**Esta solución no hizo la prueba como la pide el taller**: no hubo dos personas ni dos asistentes en conversaciones separadas. Lo que sí se hizo, y no es equivalente: se derivaron los casos de `casos.md` leyendo solo la especificación, y las ambigüedades se descubrieron después, al escribir las pruebas de aceptación y el código (sesión 02), que es otra forma, más tardía y más cara, de leer el texto con ojos distintos. Las diferencias que aparecieron entre "lo que yo creía haber escrito" y "lo que el texto permitía" fueron:

| Ambigüedad del texto | Cómo la resolví |
|---|---|
| "Termina en el primer carácter que no es de esos" frente a "un `#` + 31 caracteres no es etiqueta": ¿una serie de 35 caracteres da la etiqueta de sus primeros 30? | Se redefinió: la **serie completa** decide; si pasa de 30, no hay etiqueta (ni su prefijo). Pasó a la definición y al caso límite |
| ¿`?etiqueta=` vacío es lo mismo que sin parámetro? | Criterio 11 lo dice explícitamente: vacío es 400; sin parámetro es como hoy (criterio 12) |
| ¿`#1` es etiqueta? | Sí (los dígitos están permitidos); caso en `PruebaEtiquetas` |
| ¿Un `#` sin codificar en la URL? | El cliente no lo envía (abre el fragmento): llega `etiqueta=` vacío y es 400. Se comprobó con curl |

## Paso 8: contradicciones y huecos

Revisión del propio texto, buscando contradicciones y huecos (la hizo quien la escribió, no un lector aparte: tú puedes pedirla a otra persona o a un asistente):

| Hallazgo | ¿Real? |
|---|---|
| La definición y el caso límite de 31 caracteres se contradicen | Sí (arriba) |
| Falta decir qué pasa con `etiqueta` repetida en la consulta | Sí: no es un criterio; se anotó en el plan como decisión de implementación |
| Criterio 14 parece chocar con "no migrar" | No: no choca; las etiquetas se leen del texto |
| ¿Qué pasa con una nota de 500 caracteres llena de etiquetas? | No: ya cubierto por el límite de 10 y el de 500 |

## Paso 9: tres escenarios

Guardar con etiquetas (criterios 2 a 4), filtrar en orden (7 y 9) y etiqueta inválida (11). Obligaron a precisar: el formato exacto del error (`{ error: '#string' }`), que `etiquetas` se devuelve normalizado, y que la consulta vacía exige construir la URL a mano. Se ejecutan en la sesión 02 (`../../../02-del-plan-a-las-tareas/taller/solucion-de-referencia/pruebas/`).

## Paso 10: contra la lista del paso 1

Preguntas que no había visto al empezar y aparecieron al especificar: 4 (tildes), 5 (límite), 9 (más de 30), 10 (`#` solo), 16 (notas antiguas), 22 (la longitud de la nota incluye las etiquetas). Las seis surgieron de **un caso concreto**, no de la frase.
