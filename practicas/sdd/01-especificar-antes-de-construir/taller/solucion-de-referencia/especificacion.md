# Especificación: etiquetas en las notas

<!-- limite-etiquetas: 10 -->

## Propósito

Quien usa la bitácora escribe notas de temas distintos y hoy solo puede leerlas todas juntas.
Necesita marcar una nota con etiquetas, como `#trabajo`, y después ver solo las notas de una etiqueta.

## Historias de usuario

- Como persona que usa la bitácora, quiero marcar una nota con etiquetas al escribirla, para agruparla por tema.
- Como persona que usa la bitácora, quiero ver solo las notas de una etiqueta, para no leer las demás.

## Definiciones

- **Etiqueta**: un `#` al comienzo de una palabra, seguido de una serie de 1 a 30 caracteres que sean letras, dígitos, `_` o `-`.
  La serie termina en el primer carácter que no es de esos; si es más larga de 30, no hay etiqueta.
- **Comienzo de una palabra**: el principio del texto o justo después de un espacio.
- Dos etiquetas son **la misma** si solo se diferencian en mayúsculas y minúsculas. Las tildes sí distinguen.

## Criterios de aceptación

1. Cuando se guarda una nota, el sistema deberá reconocer como etiquetas las que contiene su texto, según las definiciones.
2. El sistema deberá tratar `#Trabajo` y `#trabajo` como la misma etiqueta.
3. Si una etiqueta aparece varias veces en una nota, entonces el sistema deberá contarla una sola vez.
4. Cuando se guarda una nota, el sistema deberá responder 201 con `{ "guardada": <texto>, "host": <texto>, "etiquetas": [<texto>…] }`,
   con las etiquetas sin `#`, en minúsculas, sin repetir y en el orden en que aparecen en la nota.
5. Si el texto de una nota no contiene etiquetas, entonces el sistema deberá responder 201 con `"etiquetas": []`.
6. Si una nota contiene más de 10 etiquetas distintas, entonces el sistema deberá responder 400 con `{ "error": "<mensaje>" }` y no guardar la nota.
7. Cuando se consulta `GET /api/notas?etiqueta=<e>`, el sistema deberá devolver solo las notas que contienen esa etiqueta.
8. El sistema deberá aceptar `<e>` con o sin `#` inicial y sin distinguir mayúsculas de minúsculas.
9. El sistema deberá devolver las notas filtradas en el mismo orden en que se guardaron.
10. Cuando ninguna nota tiene la etiqueta consultada, el sistema deberá responder 200 con una lista vacía.
11. Si `<e>` está vacía, tiene más de 30 caracteres (sin contar el `#` inicial) o contiene un carácter que no es de una etiqueta, entonces el sistema deberá responder 400 con `{ "error": "<mensaje>" }`.
12. Cuando se consulta `GET /api/notas` sin el parámetro `etiqueta`, el sistema deberá responder exactamente como hoy.
13. El sistema deberá responder las consultas con etiqueta con la misma forma de hoy: `{ "host": <texto>, "notas": [<texto>…] }`.
14. El sistema deberá tratar las notas guardadas antes de este cambio como cualquier otra: si su texto contiene etiquetas, aparecen al filtrar.
15. El sistema deberá exigir la clave para guardar y no exigirla para consultar, igual que hoy.

## Casos límite

- `#` solo, o `##x`: no hay etiqueta.
- `a#b`: no hay etiqueta (el `#` no está al comienzo de una palabra).
- `#trabajo,` y `#trabajo.`: la etiqueta es `trabajo`.
- Un `#` seguido de 31 o más caracteres permitidos seguidos: no es etiqueta (ni su primera parte de 30); la nota se guarda sin ella.
- La longitud máxima de la nota (500 caracteres) incluye las etiquetas.
- Sin notas guardadas, cualquier consulta válida devuelve una lista vacía.

## Fuera de alcance

- Renombrar, borrar o fusionar etiquetas.
- Listar todas las etiquetas existentes, con o sin conteos.
- Filtrar por varias etiquetas a la vez.
- Sugerir o autocompletar etiquetas.
- Etiquetas que contengan espacios.
- Editar etiquetas de una nota ya guardada.
- Cambios en la página web.

## Restricciones

- No se añaden dependencias: la API sigue siendo un solo archivo que usa solo el JDK.
- Ningún cliente existente debe notar el cambio: solo se añaden campos y un parámetro opcional.
- Las notas guardadas antes no se modifican ni se migran.

## Preguntas abiertas

- ¿Deben `#café` y `#cafe` ser la misma etiqueta? Por defecto: no.
- ¿Basta con 10 etiquetas por nota? Por defecto: sí.
- ¿Las notas antiguas con `#algo` deben contar como etiquetadas? Por defecto: sí (criterio 14).
- ¿Hará falta listar las etiquetas existentes? Por defecto: no.
- ¿Hará falta filtrar por varias etiquetas? Por defecto: no.
