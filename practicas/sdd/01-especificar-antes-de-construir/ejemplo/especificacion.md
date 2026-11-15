# Especificación: búsqueda de notas

## Propósito

Quien usa la bitácora acumula cientos de notas y hoy solo puede leerlas todas. Necesita encontrar
las que mencionan una palabra.

## Historias de usuario

- Como persona que usa la bitácora, quiero buscar notas por una palabra, para encontrar una nota
  sin leerlas todas.

## Criterios de aceptación

1. Cuando se consulta `GET /api/notas` sin el parámetro `q`, el sistema deberá responder exactamente
   como hoy.
2. Cuando se consulta `GET /api/notas?q=<texto>`, el sistema deberá devolver solo las notas que
   contienen `<texto>`.
3. El sistema deberá tratar como iguales las mayúsculas y las minúsculas, y las vocales con y sin
   tilde: `cafe` encuentra "Café" y "CAFÉ".
4. El sistema deberá aceptar coincidencias parciales: `pan` encuentra "panadería".
5. El sistema deberá devolver las notas en el mismo orden en que se guardaron.
6. Cuando ninguna nota coincide, el sistema deberá responder 200 con una lista vacía.
7. Si `q` está vacío o contiene solo espacios, entonces el sistema deberá responder como si `q` no
   se hubiera enviado.
8. Si `q` tiene más de 100 caracteres, entonces el sistema deberá responder 400 con un cuerpo
   `{ "error": "<mensaje>" }`.
9. El sistema deberá responder con la misma forma de hoy: `{ "host": <texto>, "notas": [<texto>…] }`.

## Casos límite

- `q` con caracteres especiales de expresiones regulares (`.`, `*`, `(`) se busca como texto literal.
- `q` con espacios al principio o al final se busca sin ellos.
- Sin notas guardadas, cualquier búsqueda devuelve una lista vacía.

## Fuera de alcance

- Buscar varias palabras a la vez o con operadores.
- Ordenar por relevancia.
- Paginación.
- Resaltar la coincidencia.
- Cambios en la página web.

## Restricciones

- No se añaden dependencias: la API sigue siendo un solo archivo que usa solo el JDK.
- La búsqueda no exige la clave, igual que la lectura actual.
- Ningún cliente existente debe notar el cambio.

## Preguntas abiertas

- ¿Hace falta un límite de resultados antes de que exista la paginación? Hoy no hay límite al listar.
