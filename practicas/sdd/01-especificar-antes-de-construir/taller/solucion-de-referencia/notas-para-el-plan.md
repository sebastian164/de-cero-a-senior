# Notas para el plan (decisiones de implementación que salieron mientras se escribía la especificación)

Se movieron aquí desde la especificación (paso 6). Ninguna es un requisito.

- Las etiquetas no se guardan aparte: se calculan del texto de la nota al guardar y al consultar. Así no hay migración (restricción) y las notas antiguas ya cuentan (criterio 14).
- Una función pura `etiquetas(texto)` que devuelve la lista normalizada; se prueba sin levantar el servidor.
- Una expresión regular para reconocer etiquetas: `(?<=^|\s)#([\p{L}\p{N}_-]{1,30})` con la salvedad del caso de 31 caracteres, que exige mirar el carácter siguiente.
- Normalizar a minúsculas con `Locale.ROOT`, para que el resultado no dependa del idioma de la máquina.
- El filtro se aplica sobre las líneas que `GET /api/notas` ya lee del archivo.
- La constante del límite de etiquetas vive en un solo sitio del código.
