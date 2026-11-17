# Paso 2: aclarar (puerta 1)

Preguntas hechas a la especificación antes de proponer un plan (a mano, como las haría un compañero que la lee por primera vez). Se actualizó `especificacion.md` donde hizo falta.

| # | Pregunta | ¿Duda real? | Resolución |
|---|---|---|---|
| 1 | ¿`#a` dentro de un número, como `#1`, es etiqueta? (los dígitos están permitidos) | Sí | Sí: `#1` es la etiqueta `1`. Se añade como caso de prueba; sin cambio en el texto |
| 2 | ¿La consulta `etiqueta=#a` llega a la API como `%23a`? ¿Y si el cliente manda `#a` sin codificar? | Sí | Un `#` sin codificar abre el fragmento de la URL y el cliente (curl, un navegador) no lo envía: la API recibe `etiqueta=` vacío y responde 400 (criterio 11). Se comprueba en `comprobar.sh`. Sin cambio en el texto |
| 3 | ¿Qué es "comienzo de una palabra" con tabuladores o saltos de línea? | Sí | Hoy se normalizan los espacios en blanco a un espacio al guardar. Se prueba con un tabulador (`x\t#y`) |
| 4 | ¿"Letras" incluye ñ y vocales con tilde? ¿Y letras de otros alfabetos? | Sí | Letras Unicode. Se añade `ñandú` a las pruebas |
| 5 | ¿Una nota muy larga con muchas etiquetas? | No | La longitud máxima de la nota ya limita el número de etiquetas posibles (500 caracteres) |
| 6 | ¿Los errores de la consulta son iguales a los de guardar? | No | Mismo formato `{ error }`, ya dicho en los criterios 6 y 11 |
| 7 | ¿Qué pasa con `etiqueta` repetida en la consulta (`?etiqueta=a&etiqueta=b`)? | Sí | Falta en la especificación. **Se añade a fuera de alcance**: "filtrar por varias etiquetas"; con repetidas se usa la primera. Ver nota abajo |
| 8 | ¿Cómo sabré que la consulta con etiqueta no exige la clave? | No | Criterio 15 |

8 preguntas, 5 dudas reales (1, 2, 3, 4, 7). La 7 obligó a decidir algo que la especificación no decía: con `?etiqueta=a&etiqueta=b` se usa la primera. Es un comportamiento de implementación que ningún criterio exige; queda anotado en el plan y no en la especificación.
