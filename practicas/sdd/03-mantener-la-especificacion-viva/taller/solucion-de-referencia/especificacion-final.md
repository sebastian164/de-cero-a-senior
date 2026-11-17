# Especificación: etiquetas en las notas

<!-- limite-etiquetas: 5 -->

## Propósito

Quien usa la bitácora escribe notas de temas distintos y hoy solo puede leerlas todas juntas.
Necesita marcar una nota con etiquetas, como `#trabajo`, y después ver solo las notas de una etiqueta.

## Definiciones

- **Etiqueta**: un `#` al comienzo de una palabra, seguido de una serie de 1 a 30 caracteres que sean letras, dígitos, `_` o `-`.
  La serie termina en el primer carácter que no es de esos; si es más larga de 30, no hay etiqueta.
- **Comienzo de una palabra**: el principio del texto o justo después de un espacio (incluido el espacio duro).
- Dos etiquetas son **la misma** si solo se diferencian en mayúsculas y minúsculas. Las tildes sí distinguen.

## Criterios de aceptación

1. Cuando se guarda una nota, el sistema deberá reconocer como etiquetas las que contiene su texto, según las definiciones.
   _Prueba: `@criterio-1` en `pruebas/src/test/java/etiquetas.feature`_
2. El sistema deberá tratar `#Trabajo` y `#trabajo` como la misma etiqueta.
   _Prueba: `@criterio-2` en `pruebas/src/test/java/etiquetas.feature`_
3. Si una etiqueta aparece varias veces en una nota, entonces el sistema deberá contarla una sola vez.
   _Prueba: `@criterio-3` en `pruebas/src/test/java/etiquetas.feature`_
4. Cuando se guarda una nota, el sistema deberá responder 201 con `{ "guardada": <texto>, "host": <texto>, "etiquetas": [<texto>…] }`,
   con las etiquetas sin `#`, en minúsculas, sin repetir y en el orden en que aparecen en la nota.
   _Prueba: `@criterio-4` en `pruebas/src/test/java/etiquetas.feature`_
5. Si el texto de una nota no contiene etiquetas, entonces el sistema deberá responder 201 con `"etiquetas": []`.
   _Prueba: `@criterio-5` en `pruebas/src/test/java/etiquetas.feature`_
6. Si una nota contiene más de 5 etiquetas distintas, entonces el sistema deberá responder 400 con `{ "error": "<mensaje>" }` y no guardar la nota.
   _Prueba: `@criterio-6` en `pruebas/src/test/java/etiquetas.feature`_
7. Cuando se consulta `GET /api/notas?etiqueta=<e>`, el sistema deberá devolver solo las notas que contienen esa etiqueta.
   _Prueba: `@criterio-7` en `pruebas/src/test/java/etiquetas.feature`_
8. El sistema deberá aceptar `<e>` con o sin `#` inicial y sin distinguir mayúsculas de minúsculas.
   _Prueba: `@criterio-8` en `pruebas/src/test/java/etiquetas.feature`_
9. El sistema deberá devolver las notas filtradas en el mismo orden en que se guardaron.
   _Prueba: `@criterio-9` en `pruebas/src/test/java/etiquetas.feature`_
10. Cuando ninguna nota tiene la etiqueta consultada, el sistema deberá responder 200 con una lista vacía.
   _Prueba: `@criterio-10` en `pruebas/src/test/java/etiquetas.feature`_
11. Si `<e>` está vacía, tiene más de 30 caracteres (sin contar el `#` inicial) o contiene un carácter que no es de una etiqueta, entonces el sistema deberá responder 400 con `{ "error": "<mensaje>" }`.
   _Prueba: `@criterio-11` en `pruebas/src/test/java/etiquetas.feature`_
12. Cuando se consulta `GET /api/notas` sin el parámetro `etiqueta`, el sistema deberá responder exactamente como hoy.
   _Prueba: `@criterio-12` en `pruebas/src/test/java/etiquetas.feature`_
13. El sistema deberá responder las consultas con etiqueta con la misma forma de hoy: `{ "host": <texto>, "notas": [<texto>…] }`.
   _Prueba: `@criterio-13` en `pruebas/src/test/java/etiquetas.feature`_
14. El sistema deberá tratar las notas guardadas antes de este cambio como cualquier otra: si su texto contiene etiquetas, aparecen al filtrar.
   _Prueba: `@criterio-14` en `pruebas/src/test/java/etiquetas.feature`_
15. El sistema deberá exigir la clave para guardar y no exigirla para consultar, igual que hoy.
   _Prueba: `@criterio-15` en `pruebas/src/test/java/etiquetas.feature`_

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
- ¿Basta con 5 etiquetas por nota? Por defecto: sí.
- ¿Las notas antiguas con `#algo` deben contar como etiquetadas? Por defecto: sí (criterio 14).
- ¿Hará falta listar las etiquetas existentes? Por defecto: no.
- ¿Hará falta filtrar por varias etiquetas? Por defecto: no.
