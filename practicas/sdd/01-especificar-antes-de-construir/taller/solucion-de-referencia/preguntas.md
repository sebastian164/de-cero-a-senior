# Paso 1 y 2: las preguntas que la frase deja sin responder

La frase: "Alguien quiere poder marcar una nota con etiquetas como `#trabajo` y luego ver solo las de una etiqueta."

| # | Pregunta | Respuesta | Estado |
|---|---|---|---|
| 1 | ¿Dónde se escribe la etiqueta: dentro del texto de la nota, o en un campo aparte? | Dentro del texto: es lo que muestra el ejemplo `#trabajo` y no cambia lo que envían los clientes | decidida |
| 2 | ¿Qué caracteres componen una etiqueta? | Letras (con o sin tilde), dígitos, `_` y `-`, de 1 a 30 | decidida |
| 3 | ¿`#Trabajo` y `#trabajo` son la misma? | Sí | decidida |
| 4 | ¿`#café` y `#cafe` son la misma? | No: las tildes distinguen | **abierta**, por defecto: distintas |
| 5 | ¿Cuántas etiquetas puede tener una nota? | Hasta 10 distintas; con más, se rechaza | **abierta**, por defecto: 10 |
| 6 | ¿Qué pasa si una nota repite una etiqueta? | Cuenta una vez | decidida |
| 7 | ¿Un `#` en medio de una palabra (`a#b`) es etiqueta? | No: la etiqueta empieza al comienzo de una palabra | decidida |
| 8 | ¿Y los signos pegados (`#trabajo,` o `#trabajo.`)? | La etiqueta termina en el primer carácter no permitido | decidida |
| 9 | ¿Una "etiqueta" de más de 30 caracteres (`#` + 31 letras)? | No se reconoce como etiqueta; la nota se guarda | decidida |
| 10 | ¿`#` solo, o `##x`? | `#` solo no es etiqueta; en `##x` no hay etiqueta (después del primer `#` no hay carácter permitido) | decidida |
| 11 | ¿Cómo se consulta por etiqueta? | `GET /api/notas?etiqueta=<e>`, la misma ruta | decidida |
| 12 | ¿Se acepta `etiqueta=%23trabajo` (con `#`)? | Sí, con o sin `#` | decidida |
| 13 | ¿Qué devuelve una etiqueta que ninguna nota tiene? | 200 con lista vacía | decidida |
| 14 | ¿Qué devuelve una etiqueta inválida (vacía, con espacios, de 31 caracteres)? | 400 con `{ "error": ... }` | decidida |
| 15 | ¿En qué orden salen las notas filtradas? | El orden en que se guardaron | decidida |
| 16 | ¿Las notas ya guardadas con `#algo` cuentan como etiquetadas? | Sí | **abierta** (negocio), por defecto: sí |
| 17 | ¿Guardar una nota con etiquetas exige la clave como hoy? | Sí, igual que hoy; leer no la exige | decidida |
| 18 | ¿La respuesta de guardar dice qué etiquetas se reconocieron? | Sí, `etiquetas`, en minúsculas y sin repetir | decidida |
| 19 | ¿Hay que poder listar todas las etiquetas existentes? | No en esta funcionalidad | **abierta** (negocio), por defecto: no |
| 20 | ¿Se puede filtrar por varias etiquetas a la vez? | No | **abierta** (negocio), por defecto: no |
| 21 | ¿Cambia la página web? | No | decidida |
| 22 | ¿La longitud máxima de la nota (500) cuenta las etiquetas? | Sí, es parte del texto | decidida |

22 preguntas; 17 decididas, 5 abiertas con respuesta por defecto (4, 5, 16, 19, 20).
