# Casos de prueba derivados de la especificación

Cada criterio tiene al menos un caso. Se derivaron leyendo solo `especificacion.md`.

| Caso | Criterio | Entrada | Resultado esperado |
|---|---|---|---|
| C1 | 1 | Guardar `ir al banco #trabajo` | `etiquetas` = `["trabajo"]` |
| C2 | 1 | Guardar `a#b #c` | `etiquetas` = `["c"]`: `a#b` no cuenta |
| C3 | 1 | Guardar `#` solo, `##x` y `# x` | `etiquetas` = `[]` en los tres |
| C4 | 1 | Guardar `#trabajo, #casa.` | `["trabajo","casa"]` |
| C5 | 1 | Guardar una nota con `#` + 31 letras seguidas | 201 y `etiquetas` = `[]` |
| C6 | 1 | Guardar una nota con `#` + 30 letras | la etiqueta de 30 letras se reconoce |
| C7 | 2 | Guardar `#Trabajo` y luego consultar `etiqueta=trabajo` | la nota aparece |
| C8 | 3 | Guardar `#a #A #a` | `["a"]` |
| C9 | 4 | Guardar `x #Uno #dos #UNO` | 201, `guardada` igual al texto enviado, `host` texto, `etiquetas` = `["uno","dos"]` |
| C10 | 5 | Guardar `sin etiquetas` | 201 con `"etiquetas": []` |
| C11 | 6 | Guardar una nota con 11 etiquetas distintas | 400 con `error`; la nota no aparece al listar |
| C12 | 6 | Guardar una nota con 10 etiquetas distintas, y otra con 11 etiquetas donde una se repite (10 distintas) | las dos se guardan (201) |
| C13 | 7 | Tres notas, dos con `#x`; consultar `etiqueta=x` | solo las dos |
| C14 | 8 | Consultar `etiqueta=%23X` y `etiqueta=x` y `etiqueta=X` | las tres respuestas son iguales |
| C15 | 9 | Guardar `1 #x`, `2`, `3 #x`; consultar `etiqueta=x` | `["1 #x", "3 #x"]` en ese orden |
| C16 | 10 | Consultar una etiqueta que ninguna nota tiene | 200 con `"notas": []` |
| C17 | 11 | Consultar con `etiqueta=` vacía | 400 con `error` |
| C18 | 11 | Consultar con 31 caracteres, con `etiqueta=a b`, con `etiqueta=a.b`, con `etiqueta=%23%23a` | 400 con `error` en los cuatro |
| C19 | 11 | Consultar con 30 caracteres, y con `#` + 30 | 200 |
| C20 | 12 | Consultar sin parámetro, antes y después del cambio | respuesta idéntica en forma y contenido |
| C21 | 13 | Consultar con etiqueta | la respuesta es exactamente `{host, notas}`: sin campos nuevos |
| C22 | 14 | Con un archivo de notas anterior al cambio que contiene `viejo #legado`, consultar `etiqueta=legado` | aparece |
| C23 | 15 | Guardar sin clave y con clave equivocada | 401 en ambos |
| C24 | 15 | Consultar con etiqueta sin clave | 200 |
